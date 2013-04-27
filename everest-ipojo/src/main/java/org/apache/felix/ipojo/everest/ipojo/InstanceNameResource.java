package org.apache.felix.ipojo.everest.ipojo;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.architecture.Architecture;
import org.apache.felix.ipojo.everest.impl.DefaultReadOnlyResource;
import org.apache.felix.ipojo.everest.impl.ImmutableResourceMetadata;
import org.apache.felix.ipojo.everest.services.ResourceMetadata;

/**
 * '/ipojo/instance/$name' resource, where $name stands for the name of an instance.
 */
public class InstanceNameResource extends DefaultReadOnlyResource {

    /**
     * The represented instance.
     */
    private final Architecture m_instance;

    /**
     * Flag indicating if the underlying Architecture service still exists.
     */
    private volatile boolean m_isStale = false;

    /**
     * The base immutable metadata of this resource.
     */
    private final ResourceMetadata m_baseMetadata;

    //TODO add relations


    /**
     * @param instance the instance represented by this resource
     */
    public InstanceNameResource(Architecture instance) {
        super(InstancesResource.PATH.addElements(instance.getInstanceDescription().getName()));
        m_instance = instance;

        // Build the immutable metadata of this instance.
        ImmutableResourceMetadata.Builder mb = new ImmutableResourceMetadata.Builder();
        mb.set("name", instance.getInstanceDescription().getName()); // String
        m_baseMetadata = mb.build();
    }

    /**
     * Set this instance resource as stale. It happens when the underlying Architecture service vanishes.
     */
    void setStale() {
        m_isStale = true;
    }

    @Override
    public synchronized ResourceMetadata getMetadata() {
        // Append mutable state to the immutable metadata.
        ImmutableResourceMetadata.Builder mb = new ImmutableResourceMetadata.Builder(m_baseMetadata);
        mb.set("state", stateAsString(m_instance.getInstanceDescription().getState())); // String
        return mb.build();
    }

    private static String stateAsString(int state) {
        switch (state) {
            case ComponentInstance.VALID:
                return "valid";
            case ComponentInstance.INVALID:
                return "invalid";
            case ComponentInstance.STOPPED:
                return "stopped";
            case ComponentInstance.DISPOSED:
                return "disposed";
            case -2:
                return "changing";
            default:
                return "unknown";
        }
    }



}
