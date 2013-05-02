package org.apache.felix.ipojo.everest.osgi.bundle;

import org.apache.felix.ipojo.everest.impl.DefaultReadOnlyResource;
import org.apache.felix.ipojo.everest.impl.DefaultRelation;
import org.apache.felix.ipojo.everest.impl.ImmutableResourceMetadata;
import org.apache.felix.ipojo.everest.osgi.OsgiResourceUtils;
import org.apache.felix.ipojo.everest.services.*;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleWire;

import java.util.ArrayList;
import java.util.List;

import static org.apache.felix.ipojo.everest.osgi.OsgiResourceUtils.BundleNamespace.BUNDLE_NAMESPACE;
import static org.apache.felix.ipojo.everest.osgi.OsgiResourceUtils.PackageNamespace.PACKAGE_NAMESPACE;
import static org.apache.felix.ipojo.everest.osgi.OsgiResourceUtils.PackageNamespace.RESOLUTION_DYNAMIC;
import static org.apache.felix.ipojo.everest.osgi.OsgiResourceUtils.metadataFrom;
import static org.apache.felix.ipojo.everest.osgi.OsgiResourceUtils.uniqueRequirementId;

/**
 * Created with IntelliJ IDEA.
 * User: ozan
 * Date: 4/22/13
 * Time: 12:52 PM
 */
public class BundleRequirementResource extends DefaultReadOnlyResource {

    private final List<BundleWire> m_wires = new ArrayList<BundleWire>();
    private final BundleRequirement m_requirement;
    private final boolean isPackage;
    private final boolean isBundle;

    public BundleRequirementResource(Path path, BundleRequirement bundleRequirement) {
        super(path.addElements(uniqueRequirementId(bundleRequirement)));
        m_requirement = bundleRequirement;

        // calculate wires coming to this requirement
        List<BundleWire> allWires = m_requirement.getRevision().getWiring().getRequiredWires(m_requirement.getNamespace());
        for (BundleWire wire : allWires) {
            if (wire.getRequirement().equals(m_requirement)) {
                m_wires.add(wire);
            }
        }

        isPackage = m_requirement.getNamespace().equals(PACKAGE_NAMESPACE);
        isBundle = m_requirement.getNamespace().equals(BUNDLE_NAMESPACE);
        String requirementId = OsgiResourceUtils.uniqueRequirementId(m_requirement);
        Relation relation = null;
        long bundleId = m_requirement.getRevision().getBundle().getBundleId();
        Path bundleHeadersPath = BundleResourceManager.getInstance().getPath().addElements(Long.toString(bundleId), BundleHeadersResource.HEADERS_PATH);
        // add relation to package import header
        if (isPackage) {
            String dynamicOrNot = RESOLUTION_DYNAMIC.equals(m_requirement.getDirectives().get(Constants.RESOLUTION_DIRECTIVE)) ? BundleHeadersResource.DYNAMIC_IMPORT_PACKAGE : BundleHeadersResource.IMPORT_PACKAGE;
            Path requirementPath = bundleHeadersPath.addElements(dynamicOrNot, requirementId);
            relation = new DefaultRelation(requirementPath, Action.READ, dynamicOrNot);
        }
        // add relation to require-bundle header
        if (isBundle) {
            Path requirementPath = bundleHeadersPath.addElements(BundleHeadersResource.REQUIRE_BUNDLE, requirementId);
            relation = new DefaultRelation(requirementPath, Action.READ, BundleHeadersResource.REQUIRE_BUNDLE);
        }
        setRelations(relation);
    }

    @Override
    public ResourceMetadata getMetadata() {
        ImmutableResourceMetadata.Builder metadataBuilder = new ImmutableResourceMetadata.Builder();
        metadataFrom(metadataBuilder, m_requirement);
        return metadataBuilder.build();
    }

    @Override
    public List<Resource> getResources() {
        ArrayList<Resource> resources = new ArrayList<Resource>();
        for (BundleWire wire : m_wires) {
            resources.add(new RequiredWireResource(getPath(), wire));
        }
        return resources;
    }
}