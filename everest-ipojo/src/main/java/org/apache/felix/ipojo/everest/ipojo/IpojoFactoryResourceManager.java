package org.apache.felix.ipojo.everest.ipojo;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.everest.services.*;

import java.util.List;

/**
 * Resource manager for iPOJO factories.
 */
@Component
@Instantiate
@Provides
public class IpojoFactoryResourceManager implements ResourceManager {

    /**
     * The path of the iPOJO factory resource manager.
     */
    public static final String IPOJO_FACTORY = "ipojo/factory";

    @Override
    public String getName() {
        return IPOJO_FACTORY;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Resource getResource(String path) {
        return null;
    }

    @Override
    public List<Resource> getResources(Resource resource, ResourceFilter filter) {
        return null;
    }

    @Override
    public List<Resource> getResources(ResourceFilter filter) {
        return null;
    }

    @Override
    public Resource process(Request request) throws IllegalActionOnResourceException, ResourceNotFoundException {
        return null;
    }

}
