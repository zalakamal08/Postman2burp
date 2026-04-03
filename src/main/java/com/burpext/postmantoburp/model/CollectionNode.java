package com.burpext.postmantoburp.model;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A JTree node that is either a folder (group of requests)
 * or a leaf (single HTTP request).
 */
public class CollectionNode extends DefaultMutableTreeNode {

    private final String displayName;
    private final boolean folder;
    private final RequestItem requestItem;

    /** Constructor for a folder node (no request attached). */
    public CollectionNode(String name) {
        super(name);
        this.displayName = name;
        this.folder = true;
        this.requestItem = null;
    }

    /** Constructor for a leaf/request node. */
    public CollectionNode(String name, RequestItem item) {
        super(name);
        this.displayName = name;
        this.folder = false;
        this.requestItem = item;
    }

    public String getDisplayName() { return displayName; }
    public boolean isFolder()      { return folder; }
    public RequestItem getRequestItem() { return requestItem; }

    @Override
    public String toString() { return displayName; }
}
