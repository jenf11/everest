package org.apache.felix.ipojo.everest.ipojo;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.Factory;
import org.apache.felix.ipojo.HandlerFactory;
import org.apache.felix.ipojo.annotations.*;
import org.apache.felix.ipojo.architecture.Architecture;
import org.apache.felix.ipojo.everest.impl.DefaultRelation;
import org.apache.felix.ipojo.everest.impl.DefaultRequest;
import org.apache.felix.ipojo.everest.impl.ImmutableResourceMetadata;
import org.apache.felix.ipojo.everest.services.*;
import org.apache.felix.ipojo.extender.ExtensionDeclaration;
import org.apache.felix.ipojo.extender.InstanceDeclaration;
import org.apache.felix.ipojo.extender.TypeDeclaration;
import org.osgi.framework.*;

import java.util.*;

import static java.lang.String.format;

/**
 * '/ipojo' resource.
 */
@Component
@Instantiate
@Provides(specifications = Resource.class)
public class IpojoRootResource extends ResourceMap<ResourceMap<?>> {

    /**
     * Path to self : "/ipojo"
     */
    public static final Path PATH = Path.from("/ipojo");

    /**
     * Path to iPOJO instances : "/ipojo/instance"
     */
    public static final Path INSTANCES = PATH.addElements("instance");

    /**
     * Path to iPOJO factories  : "/ipojo/factory"
     */
    public static final Path FACTORIES = PATH.addElements("factory");

    /**
     * Path to iPOJO handlers :  "/ipojo/handler"
     */
    public static final Path HANDLERS = PATH.addElements("handler");

    /**
     * Path to iPOJO declarations : "/ipojo/declaration"
     */
    public static final Path DECLARATIONS = PATH.addElements("declaration");

    /**
     * Path to iPOJO declarations : "/ipojo/declaration/instance"
     */
    public static final Path INSTANCE_DECLARATIONS = DECLARATIONS.addElements("instance");

    /**
     * Path to iPOJO declarations : "/ipojo/declaration/type"
     */
    public static final Path TYPE_DECLARATIONS = DECLARATIONS.addElements("type");

    /**
     * Path to iPOJO declarations : "/ipojo/declaration/extension"
     */
    public static final Path EXTENSION_DECLARATIONS = DECLARATIONS.addElements("extension");

    /**
     * Path to OSGi bundles domain : "/osgi"
     */
    public static final Path PATH_TO_OSGI = Path.from("/osgi");

    /**
     * Path to OSGi bundles domain : "/osgi/bundles"
     */
    public static final Path PATH_TO_OSGI_BUNDLES = PATH_TO_OSGI.addElements("bundles");

    /**
     * Path to OSGi services domain : "/osgi/services"
     */
    public static final Path PATH_TO_OSGI_SERVICES = PATH_TO_OSGI.addElements("services");

    /**
     * Path to OSGi packages domain : "/osgi/packages"
     */
    // public static final Path PATH_TO_OSGI_PACKAGES = PATH_TO_OSGI.addElements("packages");


    public static final String FACTORY_NAME = "factory.name";
    public static final String FACTORY_VERSION = "factory.version";
    public static final String INSTANCE_NAME = "instance.name";

    /**
     * The context of the everest iPOJO bundle.
     */
    private final BundleContext m_context;

    /**
     * The iPOJO bundle
     */
    private final Bundle m_ipojo;

    /**
     * The iPOJO component instances, index by instance name.
     */
    private final ResourceMap<InstanceResource> m_instances = new ResourceMap<InstanceResource>(INSTANCES, true, null) {

        // The request may be an instance creation on an nonexistent path.
        // In this case, we must intercept the default process to capture the instance name, etc.
        // To be eligible, the request MUST have a CREATE action and a path that is a direct child of /ipojo/instance.
        @Override
        public Resource process(Request request) throws IllegalActionOnResourceException, ResourceNotFoundException {
            if (request.action() == Action.CREATE && request.path().subtract(INSTANCES).getCount() == 1) {
                return createInstanceRequest(request);
            } else {
                return super.process(request);
            }
        }
    };

    /**
     * The iPOJO component factories, index by factory name.
     * However factory name is not unique, so for each name we have a map of instance declarations.
     * These maps are indexed by the factory version.
     */
    private final ResourceMap<ResourceMap<FactoryResource>> m_factories =
            new ResourceMap<ResourceMap<FactoryResource>>(FACTORIES, true, null);

    /**
     * The iPOJO handlers, index by namespace.
     * However handler namespace is not unique, so for each namespace we have a map of handlers.
     * These maps are indexed by the handler name.
     */
    private final ResourceMap<ResourceMap<HandlerResource>> m_handlers =
            new ResourceMap<ResourceMap<HandlerResource>>(HANDLERS, true, null);

    /**
     * Instance declarations, indexed by declared instance name.
     * However declared instance name is not unique, so for each name we have a map of instance declarations.
     * These maps are indexed by the order of arrival of the declarations (ugly, but what else?).
     */
    private final ResourceMap<ResourceMap<InstanceDeclarationResource>> m_instanceDeclarations =
            new ResourceMap<ResourceMap<InstanceDeclarationResource>>(INSTANCE_DECLARATIONS, true);

    /**
     * Type declarations, indexed by declared type name.
     * However type declaration name is not unique, so for each name we have a map of type declarations
     * These maps are indexed by the declared type version.
     */
    private final ResourceMap<ResourceMap<TypeDeclarationResource>> m_typeDeclarations =
            new ResourceMap<ResourceMap<TypeDeclarationResource>>(TYPE_DECLARATIONS, true);

    /**
     * Extension declarations, indexed by declared extension name.
     */
    private final ResourceMap<ExtensionDeclarationResource> m_extensionDeclarations =
            new ResourceMap<ExtensionDeclarationResource>(EXTENSION_DECLARATIONS, true);

    /**
     * Construct the iPOJO root resource
     *
     * @param context bundle context of the everest-ipojo bundle.
     */
    public IpojoRootResource(BundleContext context) {
        // Not observable
        super(PATH, false,
                new ImmutableResourceMetadata.Builder()
                        .set("name", "ipojo")                    // The name of this domain
                        .set("description", "The iPOJO domain")  // The description of this domain
                        .build());
        m_context = context;

        // Retrieve the iPOJO bundle + version
        Bundle b = null;
        for (Bundle bundle : context.getBundles()) {
            if ("org.apache.felix.ipojo".equals(bundle.getSymbolicName())) {
                b = bundle;
                break;
            }
        }
        m_ipojo = b;
        if (m_ipojo == null) {
            // Should never happen!
            throw new AssertionError("cannot find iPOJO bundle");
        }

        // Add the sub-resources
        addResource(m_instances, "instances", "The iPOJO component instances");
        addResource(m_factories, "factories", "The iPOJO component factories");
        addResource(m_handlers, "handlers", "The iPOJO handlers");
        ResourceMap<ResourceMap<?>> declarations = new ResourceMap<ResourceMap<?>>(DECLARATIONS, false);
        declarations.addResource(m_instanceDeclarations, "instances", "The iPOJO instance declarations");
        declarations.addResource(m_typeDeclarations, "types", "The iPOJO type declarations");
        declarations.addResource(m_extensionDeclarations, "extensions", "The iPOJO extension declarations");
        addResource(declarations, "declarations", "The iPOJO declarations");
    }

    @Override
    public ResourceMetadata getMetadata() {
        // Add metadata 'version' set to the version of the iPOJO
        return new ImmutableResourceMetadata.Builder(super.getMetadata())
                .set("version", m_ipojo.getVersion().toString()) // The version of the iPOJO bundle
                .build();
    }

    @Override
    public List<Relation> getRelations() {
        List<Relation> relations = new ArrayList<Relation>(super.getRelations());
        // Add relation 'bundle' to READ the iPOJO bundle resource
        relations.add(new DefaultRelation(
                PATH_TO_OSGI_BUNDLES.addElements(String.valueOf(m_ipojo.getBundleId())),
                Action.READ,
                "bundle",
                "The iPOJO bundle"));
        return Collections.unmodifiableList(relations);
    }

    @Override
    public <A> A adaptTo(Class<A> clazz) {
        if (clazz == Version.class) {
            // Version => used version of iPOJO
            return clazz.cast(m_ipojo.getVersion());
        } else if (clazz == Bundle.class) {
            // Bundle  => the iPOJO bundle
            return clazz.cast(m_ipojo);
        } else {
            return super.adaptTo(clazz);
        }
    }

    // Callbacks for the tracking of iPOJO component instances
    // =================================================================================================================

    @Bind(id = "instances", optional = true, aggregate = true)
    public void bindInstance(Architecture instance, ServiceReference<Architecture> ref) {
        String name = instance.getInstanceDescription().getName();
        m_instances.addResource(
                new InstanceResource(instance, ref),
                format("instance[%s]", name),
                format("iPOJO component instance '%s'", name));
    }

    @Unbind(id = "instances")
    public void unbindInstance(Architecture instance) {
        m_instances.removePath(INSTANCES.addElements(instance.getInstanceDescription().getName()));
    }

    // Callbacks for the tracking of iPOJO component factories
    // =================================================================================================================

    @Bind(id = "factories", optional = true, aggregate = true)
    public void bindFactory(Factory factory, ServiceReference<Factory> ref) {
        // Find/create the intermediate level node : ipojo/factory/$name
        String name = factory.getName();
        ResourceMap<FactoryResource> namedFactories = m_factories.addResourceMapIfAbsent(
                FACTORIES.addElements(name), true,
                String.format("factories[%s]", name),
                String.format("Factories with name '%s'", name));

        // Add the factory resource : ipojo/factory/$name/$version
        String version = String.valueOf(factory.getVersion());
        namedFactories.addResource(
                new FactoryResource(this, factory, ref),
                String.format("factory[%s]", version),
                String.format("Factory with name '%s' and version '%s", name, version));
    }

    @Unbind(id = "factories")
    public void unbindFactory(Factory factory) {
        // Find the intermediate level node : ipojo/factory/$name
        // We need to hold the ipojo/factory and ipojo/factory/$name WRITE lock at the same time because we may
        // delete the latter one if the leaving factory is the last one with that name.
        Path path = FACTORIES.addElements(factory.getName());
        ResourceMap<FactoryResource> namedFactories;
        m_factories.m_lock.writeLock().lock();
        try {
            namedFactories = m_factories.getResource(path);
            namedFactories.m_lock.writeLock().lock();
            try {
                // Remove the factory resource : ipojo/factory/$name/$version
                String version = String.valueOf(factory.getVersion());
                namedFactories.removePath(path.addElements(version));
                if (namedFactories.isEmpty()) {
                    // Last standing factory with this name
                    m_factories.removePath(path);
                }
            } finally {
                namedFactories.m_lock.writeLock().unlock();
            }
        } finally {
            m_factories.m_lock.writeLock().unlock();
        }
    }

    // Callbacks for the tracking of iPOJO handlers
    // =================================================================================================================

    @Bind(id = "handlers", optional = true, aggregate = true)
    public void bindHandler(HandlerFactory handler, ServiceReference<HandlerFactory> ref) {
        // Find/create the intermediate level node : ipojo/handler/$ns
        String ns = handler.getNamespace();
        ResourceMap<HandlerResource> nsHandlers = m_handlers.addResourceMapIfAbsent(
                HANDLERS.addElements(ns), true,
                String.format("handlers[%s]", ns),
                String.format("Handlers with namespace '%s'", ns));

        // Add the handler resource : ipojo/handler/$ns/$name
        String name = String.valueOf(handler.getName());
        nsHandlers.addResource(
                new HandlerResource(handler, ref),
                String.format("handler[%s]", name),
                String.format("Handler with namespace '%s' and name '%s", ns, name));
    }

    @Unbind(id = "handlers")
    public void unbindHandler(HandlerFactory handler) {
        // Find the intermediate level node: ipojo/handler/$ns
        // We need to hold the ipojo/handler and ipojo/handler/$ns WRITE lock at the same time because we may
        // delete the latter one if the leaving handler is the last one with that namespace.
        Path path = HANDLERS.addElements(handler.getNamespace());
        ResourceMap<HandlerResource> nsHandlers;
        m_handlers.m_lock.writeLock().lock();
        try {
            nsHandlers = m_handlers.getResource(path);
            nsHandlers.m_lock.writeLock().lock();
            try {
                // Remove the handler resource : ipojo/handler/$name/$version
                String name = String.valueOf(handler.getName());
                nsHandlers.removePath(path.addElements(name));
                if (nsHandlers.isEmpty()) {
                    // Last standing handler with this namespace
                    m_handlers.removePath(path);
                }
            } finally {
                nsHandlers.m_lock.writeLock().unlock();
            }
        } finally {
            m_handlers.m_lock.writeLock().unlock();
        }
    }

    // Callbacks for the tracking of iPOJO component instances
    // =================================================================================================================

    @Bind(id = "instanceDeclarations", optional = true, aggregate = true)
    public void bindInstanceDeclaration(InstanceDeclaration instance, ServiceReference<InstanceDeclaration> ref) {
        // Find/create the intermediate level node : ipojo/declaration/instance/$name
        String name = instance.getInstanceName();
        ResourceMap<InstanceDeclarationResource> namedInstances = m_instanceDeclarations.addResourceMapIfAbsent(
                INSTANCE_DECLARATIONS.addElements(name), true,
                String.format("instances[%s]", name),
                String.format("Instances declared with name '%s'", name));

        // Add the instance declaration resource: ipojo/declaration/instance/$name/$index
        // We need to hold the :ipojo/declaration/instance/$name WRITE lock because we need to atomically :
        // - get its size to generate the instance declaration index
        // - add the InstanceDeclarationResource resource
        namedInstances.m_lock.writeLock().lock();
        try {
            String index = String.valueOf(namedInstances.size());
            namedInstances.addResource(new InstanceDeclarationResource(index, instance, ref),
                    String.format("instance[%s]", index),
                    String.format("Instance declaration with name '%s' and index %s", name, index));
        } finally {
            namedInstances.m_lock.writeLock().unlock();
        }
    }

    // TODO check synchro: race conditions may occur

    @Unbind(id = "instanceDeclarations")
    public void unbindInstanceDeclaration(InstanceDeclaration instance, ServiceReference<InstanceDeclaration> ref) {
        // Find the intermediate level node: ipojo/declaration/instance/$name
        // We need to hold the ipojo/declaration/instance and ipojo/declaration/instance/$name WRITE lock at the same
        // time because we may delete the latter one if the leaving instance declaration is the last one with that name.
        Path path = INSTANCE_DECLARATIONS.addElements(instance.getInstanceName());
        m_instanceDeclarations.m_lock.writeLock().lock();
        try {
            ResourceMap<InstanceDeclarationResource> namedInstances = m_instanceDeclarations.getResource(path);
            namedInstances.m_lock.writeLock().lock();
            try {
                // Find in namedInstances the resource to remove, using the service reference
                InstanceDeclarationResource toRemove = null;
                for (Resource r : namedInstances.getResources()) {
                    InstanceDeclarationResource rr = (InstanceDeclarationResource) r;
                    if (ref.equals(rr.m_ref)) {
                        toRemove = rr;
                        break;
                    }
                }
                // Remove the instance declaration resource : ipojo/declaration/instance/$name/$index
                namedInstances.removeResource(toRemove);
                if (namedInstances.isEmpty()) {
                    // Last standing instance declaration with that name.
                    m_instanceDeclarations.removeResource(namedInstances);
                }
            } finally {
                namedInstances.m_lock.writeLock().unlock();
            }
        } finally {
            m_instanceDeclarations.m_lock.writeLock().unlock();
        }
    }

    @Bind(id = "typeDeclarations", optional = true, aggregate = true)
    public void bindTypeDeclaration(TypeDeclaration type, ServiceReference<TypeDeclaration> ref) {
        // Find/create the intermediate level node: ipojo/declaration/type/$name
        String name = type.getComponentName();
        ResourceMap<TypeDeclarationResource> namedTypes = m_typeDeclarations.addResourceMapIfAbsent(
                TYPE_DECLARATIONS.addElements(name), true,
                String.format("types[%s]", name),
                String.format("Types declared with name '%s'", name));

        // Add the type declaration resource: ipojo/declaration/type/$name/$version
        String version = String.valueOf(type.getComponentVersion());
        namedTypes.addResource(new TypeDeclarationResource(type, ref),
                String.format("type[%s]", version),
                String.format("Type declaration with name '%s' and version %s", name, version));
    }

    @Unbind(id = "typeDeclarations")
    public void unbindTypeDeclaration(TypeDeclaration type) {
        // Find the intermediate level node: ipojo/declaration/type/$name
        // We need to hold the ipojo/declaration/type and ipojo/declaration/type/$name WRITE lock at the same time
        // because we may delete the latter one if the leaving type declaration is the last one with that name.
        Path path = TYPE_DECLARATIONS.addElements(type.getComponentName());
        m_typeDeclarations.m_lock.writeLock().lock();
        try {
            ResourceMap<TypeDeclarationResource> namedTypes = m_typeDeclarations.getResource(path);
            namedTypes.m_lock.writeLock().lock();
            try {
                // Remove the type declaration resource:  ipojo/declaration/type/$name/$version
                namedTypes.removePath(path.addElements(String.valueOf(type.getComponentVersion())));
                if (namedTypes.isEmpty()) {
                    // Last standing declaration with that name
                    m_typeDeclarations.removeResource(namedTypes);
                }
            } finally {
                namedTypes.m_lock.writeLock().unlock();
            }
        } finally {
            m_typeDeclarations.m_lock.writeLock().unlock();
        }
    }

    @Bind(id = "extensionDeclarations", optional = true, aggregate = true)
    public void bindExtensionDeclaration(ExtensionDeclaration extension, ServiceReference<ExtensionDeclaration> ref) {
        // ipojo/declaration/extensions/$name
        String name = extension.getExtensionName();
        m_extensionDeclarations.addResource(
                new ExtensionDeclarationResource(extension, ref),
                String.format("extension[%s]", name),
                String.format("Extension declaration with name '%s'", name));
    }

    @Unbind(id = "extensionDeclarations")
    public void unbindExtensionDeclaration(ExtensionDeclaration extension) {
        // ipojo/declaration/extensions/$name
        m_extensionDeclarations.removePath(EXTENSION_DECLARATIONS.addElements(extension.getExtensionName()));
    }

    // Utility methods
    // =================================================================================================================

    /**
     * Get the reference of the iPOJO Factory service with the specified factory name and version.
     *
     * @param factoryName    name of the factory to get
     * @param factoryVersion version of the factory to get
     * @return the reference to the factory service with the specified factory name and version.
     * @throws InvalidSyntaxException if {@code factoryName} or {@code factoryVersion} contains some LDAP special
     *                                characters
     * @throws IllegalStateException  if zero or more than one factory with the specified factory name and version are
     *                                present.
     */
    private ServiceReference<Factory> getFactoryService(String factoryName, String factoryVersion)
            throws InvalidSyntaxException {
        // Build the factory selection filter.
        String filter = "(&(" + FACTORY_NAME + "=" + factoryName + ")(";
        if (factoryVersion != null) {
            filter += FACTORY_VERSION + "=" + factoryVersion + "))";
        } else {
            filter += "!(" + FACTORY_VERSION + "=*)))";
        }

        Collection<ServiceReference<Factory>> refs = m_context.getServiceReferences(Factory.class, filter);
        if (refs.isEmpty()) {
            throw new IllegalStateException(
                    format("no factory service with same name/version: %s/%s", factoryName, factoryVersion));
        } else if (refs.size() > 1) {
            // Should never happen!
            throw new IllegalStateException(
                    format("multiple factory service with same name/version: %s/%s", factoryName, factoryVersion));
        }
        return refs.iterator().next();
    }

    /**
     * Process a component instance creation request.
     *
     * @param request the instance creation request
     * @return the resource representing the created instance.
     * @throws IllegalActionOnResourceException
     *          if the instance creation failed for any reason.
     */
    private Resource createInstanceRequest(Request request) throws IllegalActionOnResourceException {
        // Retrieve the mandatory factory name parameter
        String factoryName = null;
        try {
            factoryName = request.get(FACTORY_NAME, String.class);
        } catch (Exception e) {
            // Ignored: factoryName == null
        }
        if (factoryName == null) {
            throw new IllegalActionOnResourceException(request, this,
                    "missing required string property: " + FACTORY_NAME);
        }

        // Retrieve the optional factory version parameter
        String factoryVersion = null;
        try {
            factoryVersion = request.get(FACTORY_VERSION, String.class);
        } catch (Exception e) {
            // Ignored: factoryVersion == null
        }
        if (factoryVersion != null) {
            factoryVersion = factoryVersion.trim();
        }

        // Retrieve the instance name
        // It is the last element of the requested path, as other cases has been filtered out in the calling method.
        String instanceName = request.path().getLast();

        // Configure the instance with the request parameters.
        // Remove the special properties that are not part of the instance configuration.
        Hashtable<String, Object> config = new Hashtable<String, Object>(request.parameters());
        config.remove(FACTORY_NAME);
        config.remove(FACTORY_VERSION);
        if (config.put(INSTANCE_NAME, instanceName) != null) {
            throw new IllegalActionOnResourceException(request, this,
                    INSTANCE_NAME + " property cannot be specified in the request parameter");
        }

        // Get the factory service.
        ServiceReference<Factory> factoryRef;
        Factory factory;
        try {
            factoryRef = getFactoryService(factoryName, factoryVersion);
            factory = m_context.getService(factoryRef);
        } catch (Exception e) {
            IllegalActionOnResourceException ee = new IllegalActionOnResourceException(request, this,
                    format("cannot get factory service with specified name/version: %s/%s",
                            factoryName, factoryVersion));
            ee.initCause(e);
            throw ee;
        }

        // Create the component instance with the configuration.
        ComponentInstance instance;
        try {
            instance = factory.createComponentInstance(config);
        } catch (Exception e) {
            IllegalActionOnResourceException ee = new IllegalActionOnResourceException(
                    request, this, "cannot create component instance: " + instanceName);
            ee.initCause(e);
            throw ee;
        } finally {
            try {
                m_context.ungetService(factoryRef);
            } catch (Exception e) {
                // Swallow the exception.
            }
        }

        // The instance has been created :
        // - If it exposes the Architecture service, the corresponding resource has already been created.
        // - Else, the instance is not public: we a one-shot generated resource.
        try {
            return process(new DefaultRequest(Action.READ, INSTANCES.addElements(instance.getInstanceName()), null));
        } catch (ResourceNotFoundException e) {
            // Not public, however we've got the ComponentInstance object, so we can return some valuable info.
            try {
                return new Builder()
                        .fromPath(INSTANCES.addElements(instanceName))
                                // TODO add some metadata + relations here
                        .build();
            } catch (IllegalResourceException e1) {
                // Should never happen!
                throw new AssertionError(e1);
            }
        }
    }

}