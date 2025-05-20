package de.knudev.aiexport.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Action class to demonstrate how to interact with the IntelliJ Platform.
 * The action exports rules from the workspace.xml file into a usable string.
 */
public class ExportSettings extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * This default constructor is used by the IntelliJ Platform framework to instantiate this class based on plugin.xml
     * declarations. Only needed in {@link ExportSettings} class because a second constructor is overridden.
     */
    public ExportSettings() {
        super();
    }

    /**
     * This constructor is used to support dynamically added menu actions.
     * It sets the text, description to be displayed for the menu item.
     * Otherwise, the default AnAction constructor is used by the IntelliJ Platform.
     *
     * @param text        The text to be displayed as a menu item.
     * @param description The description of the menu item.
     * @param icon        The icon to be used with the menu item.
     */
    @SuppressWarnings("ActionPresentationInstantiatedInCtor") // via DynamicActionGroup
    public ExportSettings(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
        super(text, description, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project currentProject = event.getProject();
        if (currentProject == null) {
            return;
        }

        String workspaceRules = exportWorkspaceRules(currentProject);

        if (workspaceRules != null && !workspaceRules.isEmpty()) {
            // Show the exported rules in a message dialog
            Messages.showMessageDialog(
                    currentProject,
                    "Exported Rules:\n\n" + workspaceRules,
                    "Workspace Rules Export",
                    Messages.getInformationIcon());

            // Here you could also save the rules to a file, copy to clipboard, etc.
        } else {
            Messages.showMessageDialog(
                    currentProject,
                    "No workspace.xml file found or no rules could be extracted.",
                    "Workspace Rules Export",
                    Messages.getWarningIcon());
        }
    }

    /**
     * Exports rules from workspace.xml file to a string
     *
     * @param project The current project
     * @return String containing the exported rules, or null if an error occurred
     */
    @Nullable
    private String exportWorkspaceRules(Project project) {
        try {
            // Get the path to the workspace.xml file
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                return null;
            }

            Path workspacePath = Paths.get(projectPath, ".idea", "workspace.xml");
            Path aiDirectory = Paths.get(projectPath, ".ai");

            if (!Files.exists(workspacePath)) {
                return null;
            }

            // Create .ai directory if it doesn't exist
            if (!Files.exists(aiDirectory)) {
                Files.createDirectory(aiDirectory);
            }

            // Parse the XML file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(workspacePath.toFile());
            document.getDocumentElement().normalize();

            // Extract rules
            StringBuilder summaryBuilder = new StringBuilder();
            List<String> exportedFiles = new ArrayList<>();

            // Look specifically for AIAssistantCustomInstructionsStorage component
            NodeList componentList = document.getElementsByTagName("component");
            for (int i = 0; i < componentList.getLength(); i++) {
                Node componentNode = componentList.item(i);
                if (componentNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element componentElement = (Element) componentNode;
                    String componentName = componentElement.getAttribute("name");

                    // Target the AIAssistantCustomInstructionsStorage component
                    if (componentName.equals("AIAssistantCustomInstructionsStorage")) {
                        // Find the instructions option
                        NodeList optionNodes = componentElement.getElementsByTagName("option");
                        for (int j = 0; j < optionNodes.getLength(); j++) {
                            Element optionElement = (Element) optionNodes.item(j);
                            if (optionElement.getAttribute("name").equals("instructions")) {
                                // Process the map entries
                                NodeList mapNodes = optionElement.getElementsByTagName("map");
                                if (mapNodes.getLength() > 0) {
                                    Element mapElement = (Element) mapNodes.item(0);
                                    NodeList entryNodes = mapElement.getElementsByTagName("entry");

                                    for (int k = 0; k < entryNodes.getLength(); k++) {
                                        Element entryElement = (Element) entryNodes.item(k);
                                        String actionId = entryElement.getAttribute("key");

                                        // Extract the content
                                        NodeList valueNodes = entryElement.getElementsByTagName("value");
                                        if (valueNodes.getLength() > 0) {
                                            Element valueElement = (Element) valueNodes.item(0);
                                            NodeList instructionNodes = valueElement.getElementsByTagName("AIAssistantStoredInstruction");
                                            if (instructionNodes.getLength() > 0) {
                                                Element instructionElement = (Element) instructionNodes.item(0);

                                                // Get the content option
                                                NodeList contentOptionNodes = instructionElement.getElementsByTagName("option");
                                                for (int m = 0; m < contentOptionNodes.getLength(); m++) {
                                                    Element contentOption = (Element) contentOptionNodes.item(m);
                                                    if (contentOption.getAttribute("name").equals("content")) {
                                                        String content = contentOption.getAttribute("value");

                                                        // Create markdown file name from actionId
                                                        String fileName = actionId.replaceAll("[^a-zA-Z0-9-]", "_") + ".md";
                                                        Path filePath = aiDirectory.resolve(fileName);

                                                        // Write content to file
                                                        Files.writeString(filePath, "# " + actionId + "\n\n" + content);
                                                        exportedFiles.add(fileName);

                                                        summaryBuilder.append("Exported: ").append(fileName).append("\n");
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return exportedFiles.isEmpty() ? "No instructions found to export." :
                    "Successfully exported " + exportedFiles.size() + " files to .ai directory:\n" + summaryBuilder;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            // Log the error or handle it as appropriate
            return "Error exporting instructions: " + e.getMessage();
        }
    }

    @Override
    public void update(AnActionEvent e) {
        // Set the availability based on whether a project is open
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}