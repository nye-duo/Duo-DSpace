package no.uio.duo;

import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.dspace.app.xmlui.aspect.xmlworkflow.AbstractXMLUIAction;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.Reference;
import org.dspace.app.xmlui.wing.element.ReferenceSet;
import org.dspace.app.xmlui.wing.element.Row;
import org.dspace.app.xmlui.wing.element.Table;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Stack;

public class XmlUICollectionAssignmentUI extends AbstractXMLUIAction
{
    @Override
    public void addBody(Body body)
            throws SAXException, WingException, SQLException, IOException, AuthorizeException
    {
        Item item = workflowItem.getItem();
        Collection collection = workflowItem.getCollection();
        Request request = ObjectModelHelper.getRequest(objectModel);
        String actionURL = contextPath + "/handle/"+collection.getHandle() + "/xmlworkflow";

        Division div = body.addInteractiveDivision("perform-task", actionURL, Division.METHOD_POST, "primary workflow");
        div.setHead("Assign the Item's Collections");
        div.addPara("Select collections to which the item will be mapped");

        addWorkflowItemInformation(div, item, request);

        div.addSimpleHTMLFragment(false, "The item is being submitted to the collection: <strong>" + collection.getName() + " (" + collection.getHandle() + ")</strong>");

        Collection[] collections = item.getCollections();
        List current = null;
        if (collections.length > 0)
        {
            div.addPara("The item is currently mapped to the following collections");
            current = div.addList("current_collections");
        }
        else
        {
            div.addPara("This item is not currently mapped to any other collections");
        }
        for (Collection col : collections)
        {
            current.addItem(col.getName() + " ( " + col.getHandle() + " )");
        }

        div.addPara("Select collections to add the item to:");

        TreeNode root = buildTree(Community.findAllTop(context));
        //ReferenceSet referenceSet = div.addReferenceSet("community-browser",
	    //            ReferenceSet.TYPE_SUMMARY_LIST,null,"hierarchy");

        java.util.List<TreeNode> rootNodes = root.getChildrenOfType(Constants.COMMUNITY);

        for (TreeNode node : rootNodes)
        {
            buildSelector(div, node);
        }

        /*
        Community[] communities = Community.findAll(context);
        List coms = null;
        if (communities.length > 0)
        {
            coms = div.addList("communities");
        }
        for (Community com : communities)
        {
            coms.addItem(com.getName() + " (" + com.getHandle() + ")");
        }
        */

        Table table = div.addTable("workflow-actions", 1, 1);

        // finish the workflow stage
        Row row = table.addRow();
        row.addCell().addButton("submit_finished").setValue("Save and Finish");

        // Reject item
        row.addCell().addButton("submit_save").setValue("Save");

        // Everyone can just cancel
        row.addCell().addButton("submit_leave").setValue("Cancel");
    }

    public void buildSelector(Division div, TreeNode node) throws WingException
    {
        DSpaceObject dso = node.getDSO();

        // Add all the sub-collections;
        java.util.List<TreeNode> collectionNodes = node.getChildrenOfType(Constants.COLLECTION);
        if (collectionNodes != null && collectionNodes.size() > 0)
        {
            org.dspace.app.xmlui.wing.element.List colList = div.addList("collections");
            for (TreeNode collectionNode : collectionNodes)
            {
                collectionSet.addReference(collectionNode.getDSO());
            }
        }

        // Add all the sub-communities
        java.util.List<TreeNode> communityNodes = node.getChildrenOfType(Constants.COMMUNITY);
        if (communityNodes != null && communityNodes.size() > 0)
        {
            ReferenceSet communitySet = objectInclude.addReferenceSet(ReferenceSet.TYPE_SUMMARY_LIST);

            for (TreeNode communityNode : communityNodes)
            {
                buildReferenceSet(communitySet,communityNode);
            }
        }
    }

    public void buildReferenceSet(ReferenceSet referenceSet, TreeNode node) throws WingException
    {
        DSpaceObject dso = node.getDSO();

        Reference objectInclude = referenceSet.addReference(dso);

        // Add all the sub-collections;
        java.util.List<TreeNode> collectionNodes = node.getChildrenOfType(Constants.COLLECTION);
        if (collectionNodes != null && collectionNodes.size() > 0)
        {
            ReferenceSet collectionSet = objectInclude.addReferenceSet(ReferenceSet.TYPE_SUMMARY_LIST);

            for (TreeNode collectionNode : collectionNodes)
            {
                collectionSet.addReference(collectionNode.getDSO());
            }
        }

        // Add all the sub-communities
        java.util.List<TreeNode> communityNodes = node.getChildrenOfType(Constants.COMMUNITY);
        if (communityNodes != null && communityNodes.size() > 0)
        {
            ReferenceSet communitySet = objectInclude.addReferenceSet(ReferenceSet.TYPE_SUMMARY_LIST);

            for (TreeNode communityNode : communityNodes)
            {
                buildReferenceSet(communitySet,communityNode);
            }
        }
    }

/**
     * construct a tree structure of communities and collections. The results
     * of this hierarchy are cached so calling it multiple times is acceptable.
     *
     * @param communities The root level communities
     * @return A root level node.
     */
    private TreeNode buildTree(Community[] communities) throws SQLException
    {
        int maxDepth = 10;

        TreeNode newRoot = new TreeNode();

        // Setup for breath-first traversal
        Stack<TreeNode> stack = new Stack<TreeNode>();

        for (Community community : communities)
        {
            stack.push(newRoot.addChild(community));
        }

        while (!stack.empty())
        {
            TreeNode node = stack.pop();

            // Short circuit if we have reached our max depth.
            if (node.getLevel() >= maxDepth)
            {
                continue;
            }

            // Only communities nodes are pushed on the stack.
            Community community = (Community) node.getDSO();

            for (Community subcommunity : community.getSubcommunities())
            {
                stack.push(node.addChild(subcommunity));
            }

            // Add any collections to the document.
            for (Collection collection : community.getCollections())
            {
                node.addChild(collection);
            }

        }

        return newRoot;
    }

    /**
     * Private class to represent the tree structure of communities & collections.
     */
    protected static class TreeNode
    {
        /** The object this node represents */
        private DSpaceObject dso;

        /** The level in the hierarchy that this node is at. */
        private int level;

        /** All children of this node */
        private java.util.List<TreeNode> children = new ArrayList<TreeNode>();

        /**
         * Construct a new root level node
         */
        public TreeNode()
        {
            // Root level node is add the zero level.
            this.level = 0;
        }

        /**
         * @return The DSpaceObject this node represents
         */
        public DSpaceObject getDSO()
        {
            return this.dso;
        }

        /**
         * Add a child DSpaceObject
         *
         * @param dso The child
         * @return A new TreeNode object attached to the tree structure.
         */
        public TreeNode addChild(DSpaceObject dso)
        {
            TreeNode child = new TreeNode();
            child.dso = dso;
            child.level = this.level + 1;
            children.add(child);
            return child;
        }

        /**
         * @return The current level in the hierarchy of this node.
         */
        public int getLevel()
        {
            return this.level;
        }

        /**
         * @return All children
         */
        public java.util.List<TreeNode> getChildren()
        {
            return children;
        }

        /**
         * @return All children of the given @type.
         */
        public java.util.List<TreeNode> getChildrenOfType(int type)
        {
            java.util.List<TreeNode> results = new ArrayList<TreeNode>();
            for (TreeNode node : children)
            {
                if (node.dso.getType() == type)
                {
                    results.add(node);
                }
            }
            return results;
        }
    }


    private void addCommColStruct(Division div)
            throws WingException, SQLException
    {
        div.addPara("Select collections to add the item to");
        int depth = 1;
        this.recurseComCol(div, depth, null);

    }

    private void recurseComCol(Division div, int depth, Community parent)
            throws SQLException, WingException
    {
        // get the communities and collections to be listed
        Community[] communities = this.listCommunities(parent);
        Collection[] collection = null;
        if (parent != null)
        {
            Collection[] collections = parent.getCollections();
        }

        List comColList = null;
        if (communities.length > 0 || (collection != null && collection.length > 0))
        {
            comColList = div.addList("comcol" + Integer.toString(depth) + "-" + "");
        }
        for (Community com : communities)
        {
            org.dspace.app.xmlui.wing.element.Item listItem = comColList.addItem();
            listItem.addText(com.getName() + " (" + com.getHandle() + ")");
            comColList.addItem();
        }

        if (parent != null)
        {
            Collection[] collections = parent.getCollections();
            if (collections.length > 0 && comColList == null)
            {

            }
            for (Collection col : collections)
            {

            }
        }

        // this.recurseComCol();
    }

    private Community[] listCommunities(Community root)
            throws SQLException
    {
        if (root == null)
        {
            return Community.findAll(context);
        }
        return root.getSubcommunities();
    }
}
