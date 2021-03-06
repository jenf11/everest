/*
 * Copyright 2013 OW2 Chameleon
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ow2.chameleon.everest.ipojo;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.Factory;
import org.apache.felix.ipojo.FactoryStateListener;
import org.apache.felix.ipojo.IPojoFactory;
import org.ow2.chameleon.everest.core.Everest;
import org.ow2.chameleon.everest.impl.DefaultReadOnlyResource;
import org.ow2.chameleon.everest.impl.DefaultRelation;
import org.ow2.chameleon.everest.impl.DefaultRequest;
import org.ow2.chameleon.everest.impl.ImmutableResourceMetadata;
import org.ow2.chameleon.everest.services.*;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.ow2.chameleon.everest.ipojo.IpojoRootResource.*;

/**
 * '/ipojo/factory/$name/$version' resource.
 */
public class FactoryResource extends DefaultReadOnlyResource implements FactoryStateListener {

    /**
     * The enclosing iPOJO root resource.
     */
    private final IpojoRootResource m_ipojo;

    /**
     * The underlying Factory service.
     */
    private final WeakReference<Factory> m_factory;

    @SuppressWarnings("deprecation")
    public FactoryResource(IpojoRootResource ipojo, Factory factory, ServiceReference<Factory> ref) {
        super(FACTORIES.addElements(factory.getName(), String.valueOf(factory.getVersion())),
                new ImmutableResourceMetadata.Builder()
                        .set("name", factory.getName())
                        .set("version", factory.getVersion())
                        .set("className", factory.getClassName())
                        .build());
        m_ipojo = ipojo;
        m_factory = new WeakReference<Factory>(factory);
        factory.addFactoryStateListener(this);
        // Set the immutable relations
        List<Relation> relations = new ArrayList<Relation>();
        relations.add(new DefaultRelation(
                PATH_TO_OSGI_SERVICES.addElements(String.valueOf(ref.getProperty(Constants.SERVICE_ID))),
                Action.READ,
                "service",
                "The Factory OSGi service"));
        relations.add(new DefaultRelation(
                PATH_TO_OSGI_BUNDLES.addElements(String.valueOf(factory.getBundleContext().getBundle().getBundleId())),
                Action.READ,
                "bundle",
                "The declaring OSGi bundle"));
        relations.add(new DefaultRelation(
                TYPE_DECLARATIONS.addElements(factory.getName(), String.valueOf(factory.getVersion())),
                Action.READ,
                "declaration",
                "The declaration of this factory")); // May not exist! Do we mind? Really?
        // Add relation 'requiredHandler[$ns:$name]' to READ the handlers required by this factory
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) factory.getRequiredHandlers();
        for (String nsName : required) {
            int i = nsName.lastIndexOf(':');
            String ns = nsName.substring(0, i);
            String name = nsName.substring(i + 1);
            relations.add(new DefaultRelation(
                    HANDLERS.addElements(ns, name),
                    Action.READ,
                    "requiredHandler[" + nsName + "]",
                    String.format("Required handler '%s'", nsName)));
        }
        setRelations(relations);
    }

    @Override
    public ResourceMetadata getMetadata() {
        Factory f = m_factory.get();
        ResourceMetadata m = super.getMetadata();
        if (f == null) {
            // Reference has been released
            return m;
        }
        // Add dynamic metadata
        return new ImmutableResourceMetadata.Builder(m)
                .set("state", stateAsString(f.getState())) // String
                .set("missingHandlers", f.getMissingHandlers()) // List<String>
                .build();
    }

    @Override
    public List<Relation> getRelations() {
        List<Relation> r = super.getRelations();
        Factory f = m_factory.get();
        if (f == null) {
            // Reference has been released
            return r;
        }
        // Add dynamic relations
        r = new ArrayList<Relation>(r);
        for (String instanceName : getCreatedInstanceNames(f)) {
            r.add(new DefaultRelation(
                    INSTANCES.addElements(instanceName),
                    Action.READ,
                    "instance[" + instanceName + "]",
                    "Instance '" + instanceName + "'"));
        }
        return Collections.unmodifiableList(r);
    }

    @Override
    public boolean isObservable() {
        return true;
    }

    @Override
    public <A> A adaptTo(Class<A> clazz) {
        if (clazz == Factory.class) {
            // Returns null if reference has been released
            return clazz.cast(m_factory.get());
        } else {
            return super.adaptTo(clazz);
        }
    }

    public static String stateAsString(int state) {
        switch (state) {
            case Factory.VALID:
                return "valid";
            case Factory.INVALID:
                return "invalid";
            default:
                return "unknown";
        }
    }

    @Override
    public Resource create(Request request) throws IllegalActionOnResourceException {
        Factory factory = m_factory.get();
        if (factory == null) {
            throw new IllegalActionOnResourceException(request, this, "Factory has gone");
        }

        // Get configuration of the component instance to create.
        Hashtable<String, Object> config;
        if (request.parameters() != null) {
            config = new Hashtable<String, Object>(request.parameters());
        } else {
            config = new Hashtable<String, Object>();
        }

        // Create the instance.
        ComponentInstance i;
        try {
            i = factory.createComponentInstance(config);
        } catch (Exception e) {
            IllegalActionOnResourceException ee = new IllegalActionOnResourceException(request, this,
                    "cannot create component instance");
            ee.initCause(e);
            throw ee;
        }

        // Tricky part : return the resource representing the created instance.
        Resource r;
        try {
            r = m_ipojo.process(new DefaultRequest(
                    Action.READ,
                    Path.from("/ipojo/instance").addElements(i.getInstanceName()),
                    null));
        } catch (ResourceNotFoundException e) {
            // An instance has been created, however its Architecture service is not present.
            // => Generate a fake instance resource
            return InstanceResource.fakeInstanceResource(i);
        }
        return r;
    }

    @Override
    public Resource delete(Request request) throws IllegalActionOnResourceException {
        // The factory must be destroyed.
        IPojoFactory f = (IPojoFactory) m_factory.get();
        if (f == null) {
            throw new IllegalActionOnResourceException(request, this, "Factory has gone");
        }
        Method weapon = null;
        try {
            weapon = IPojoFactory.class.getDeclaredMethod("dispose");
            weapon.setAccessible(true);
            // FATALITY!!!
            weapon.invoke(f);
            // Rest in peace little factory!
        } catch (Exception e) {
            throw new IllegalStateException("cannot kill factory", e);
        } finally {
            // It's a bad idea to let kids play with such a weapon...
            if (weapon != null) {
                weapon.setAccessible(false);
            }
        }
        // This resource should now have be auto-removed from its parent and marked as stale, since the represented Factory service has gone (forever).
        // Assassin may want to analyze the cadaver, so let's return it.
        return this;
    }

    // Get the instances created by this factory
    // This is a hack!
    private Set<String> getCreatedInstanceNames(Factory factory) {
        Field weapon = null;
        try {
            weapon = IPojoFactory.class.getDeclaredField("m_componentInstances");
            weapon.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ComponentInstance> instances = (Map<String, ComponentInstance>)weapon.get(factory);
            return instances.keySet();
        } catch (Exception e) {
            throw new RuntimeException("cannot get factory created instances", e);
        } finally {
            if (weapon != null) {
                weapon.setAccessible(false);
            }
        }
    }

    public void stateChanged(Factory factory, int newState) {
        // Fire UPDATED event
        Everest.postResource(ResourceEvent.UPDATED, this);
    }

    public void cleanup() {
        Factory f = m_factory.get();
        if (f == null) {
            return;
        }
        f.removeFactoryStateListener(this);
    }
}
