$OLD_DSPACE="/www/tmp/dspace-1.8.2-src-release"
$NEW_DSPACE="/www/tmp/DSpace"

# copy localised webapp (themes, messages)
cp -r $OLD_DSPACE/dspace/modules/xmlui/src/main/webapp/* $NEW_DSPACE/dspace/modules/xmlui/src/main/webapp/

# copy old configuration
cp -r $OLD_DSPACE/dspace/config/* $NEW_DSPACE/dspace/config

# custom java classes
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/aspect/artifactbrowser/CommunityBrowser.java $NEW_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/aspect/artifactbrowser/
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/aspect/artifactbrowser/CommunityViewer.java $NEW_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/aspect/artifactbrowser/
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/resources/aspects/ViewArtifacts/sitemap.xmap $NEW_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/resources/aspects/ViewArtifacts/
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/resources/aspects/BrowseArtifacts/sitemap.xmap $NEW_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/resources/aspects/BrowseArtifacts/
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/aspect/artifactbrowser/CommunityChildren.java $NEW_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/aspect/artifactbrowser/
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/utils/CommunityTreeHelper.java $NEW_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/java/org/dspace/app/xmlui/utils/
cp $OLD_DSPACE/dspace-xmlui/dspace-xmlui-api/src/main/resources/aspects/SearchArtifacts/sitemap.xmap $NEW_DSPACE//dspace-xmlui/dspace-xmlui-api/src/main/resources/aspects/SearchArtifacts/