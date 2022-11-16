// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;

/**
 * A single group in the grouping result model. A group may contain any number of results (stored
 * as fields, use {@link #getField(String)} to access), {@link GroupList} and {@link HitList}. Use the {@link
 * com.yahoo.search.grouping.GroupingRequest#getResultGroup(com.yahoo.search.Result)} to retrieve an instance of this.
 *
 * @author Simon Thoresen Hult
 */
public class Group extends HitGroup {

    private final GroupId groupId;

    /**
     * Creates a new instance of this class.
     *
     * @param groupId the id to assign to this group
     * @param rel     the relevance of this group
     */
    public Group(GroupId groupId, Relevance rel) {
        super(groupId.toString(), rel);
        setMeta(false);
        setAuxiliary(true);
        this.groupId = groupId;
    }

    /** Returns the id of this group. This is a model of the otherwise flattened {@link #getId() hit id}. */
    public GroupId getGroupId() {
        return groupId;
    }

    /**
     * Returns the {@link HitList} with the given label. The label is the one given to the {@link
     * com.yahoo.search.grouping.request.EachOperation} that generated the list. This method returns null if no such
     * list was found.
     *
     * @param label the label of the list to return
     * @return the requested list, or null
     */
    public HitList getHitList(String label) {
        for (Hit hit : this) {
            if (!(hit instanceof HitList)) {
                continue;
            }
            HitList lst = (HitList)hit;
            if (!label.equals(lst.getLabel())) {
                continue;
            }
            return lst;
        }
        return null;
    }

    /**
     * Returns the {@link GroupList} with the given label. The label is the one given to the {@link
     * com.yahoo.search.grouping.request.EachOperation} that generated the list. This method returns null if no such
     * list was found.
     *
     * @param label the label of the list to return
     * @return the requested list, or null
     */
    public GroupList getGroupList(String label) {
        for (Hit hit : this) {
            if (!(hit instanceof GroupList)) {
                continue;
            }
            GroupList lst = (GroupList)hit;
            if (!label.equals(lst.getLabel())) {
                continue;
            }
            return lst;
        }
        return null;
    }

}
