package org.apache.felix.ipojo.everest.everestApi.casa;

import org.apache.felix.ipojo.everest.impl.DefaultReadOnlyResource;
import org.apache.felix.ipojo.everest.impl.DefaultRelation;
import org.apache.felix.ipojo.everest.services.Action;
import org.apache.felix.ipojo.everest.services.Path;
import org.apache.felix.ipojo.everest.services.Relation;
import org.apache.felix.ipojo.everest.services.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: root
 * Date: 11/07/13
 * Time: 14:07
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractResourceCollection extends DefaultReadOnlyResource {

    /**
     * Constructor, same as {@code DefaultReadOnlyResource}
     *
     * @param path path of the resource
     */
    public AbstractResourceCollection(Path path) {
        super(path);
    }

    /**
     * Extracts the direct children and add a {@literal READ} relation to them.
     *
     * @return list of relations
     */
    public List<Relation> getRelations() {
        List<Relation> relations = new ArrayList<Relation>();
        relations.addAll(super.getRelations());
        for (Resource resource : getResources()) {
            int size = getCanonicalPath().getCount();
            String name = resource.getCanonicalPath().getElements()[size];
            relations.add(new DefaultRelation(resource.getCanonicalPath(), Action.READ, getCanonicalPath().getLast() + ":" + name,
                    "Get " + name));
        }
        return relations;
    }


}