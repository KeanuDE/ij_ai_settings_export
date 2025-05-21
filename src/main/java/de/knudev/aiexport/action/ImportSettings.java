package de.knudev.aiexport.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Action class to import AI Assistant custom instructions from .ai directory files into workspace.xml.
 * This is the complementary action to ExportSettings.
 */
public class ImportSettings extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Default constructor required by IntelliJ Platform framework
     */
    public ImportSettings() {
        super();
    }

    /**
     * Constructor for dynamic menu action
     *
     * @param text        The text to be displayed as a menu item
     * @param description The description of the menu item
     * @param icon        The icon to be used with the menu item
     */
    @SuppressWarnings("ActionPresentationInstantiatedInCtor")
    public ImportSettings(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
        super(text, description, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project currentProject = event.getProject();
        if (currentProject == null) {
            return;
        }

        String result = importInstructions(currentProject);

        if (result != null) {
            Messages.showMessageDialog(
                    currentProject,
                    result,
                    "AI Assistant Instructions Import",
                    Messages.getInformationIcon());
        } else {
            Messages.showMessageDialog(
                    currentProject,
                    "Failed to import instructions. Check IDE logs for details.",
                    "AI Assistant Instructions Import",
                    Messages.getErrorIcon());
        }
    }

    /**
     * Imports instructions from the .ai directory into workspace.xml
     *
     * @param project The current project
     * @return Result message describing the import operation
     */
    @Nullable
    private String importInstructions(Project project) {
        try {
            // Get the path to the workspace.xml file and .ai directory
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                return "Project path not found.";
            }

            Path workspacePath = Paths.get(projectPath, ".idea", "workspace.xml");
            Path aiDirectory = Paths.get(projectPath, ".ai");

            if (!Files.exists(workspacePath)) {
                return "workspace.xml not found in the project.";
            }

            if (!Files.exists(aiDirectory)) {
                return "No .ai directory found. Nothing to import.";
            }

            // Parse the workspace.xml file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(workspacePath.toFile());
            document.getDocumentElement().normalize();

            // Read all .md files from .ai directory
            Map<String, String> instructionsMap = readInstructionsFromFiles(aiDirectory);
            if (instructionsMap.isEmpty()) {
                return "No instruction files found in .ai directory.";
            }

            // Find or create the AIAssistantCustomInstructionsStorage component
            Element aiComponent = findOrCreateAiComponent(document);

            // Update the component with the imported instructions
            updateAiComponentWithInstructions(aiComponent, instructionsMap, document);

            // Save the changes back to workspace.xml
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(workspacePath.toFile());
            transformer.transform(source, result);

            // Refresh the virtual file to make sure IntelliJ sees the changes
            VirtualFile workspaceVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(workspacePath.toString());
            if (workspaceVirtualFile != null) {
                workspaceVirtualFile.refresh(false, false);
            }

            return "Successfully imported " + instructionsMap.size() + " instruction files into workspace.xml.";
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            return "Error importing instructions: " + e.getMessage();
        }
    }

    /**
     * Reads all .md files from the .ai directory and extracts action IDs and content
     *
     * @param aiDirectory Path to the .ai directory
     * @return Map of action IDs to instruction content
     */
    private Map<String, String> readInstructionsFromFiles(Path aiDirectory) throws IOException {
        Map<String, String> instructionsMap = new HashMap<>();
        
        List<Path> mdFiles = Files.list(aiDirectory)
                .filter(file -> file.toString().endsWith(".md"))
                .toList();
        
        for (Path file : mdFiles) {
            String content = Files.readString(file);
            
            // Extract action ID from the first line (assuming format: "# actionId")
            Pattern pattern = Pattern.compile("^#\\s+([^\\s]+)");
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                String actionId = matcher.group(1);
                // Remove the first line (header) from the content
                String instruction = content.substring(matcher.end()).trim();
                instructionsMap.put(actionId, instruction);
            }
        }
        
        return instructionsMap;
    }

    private Element findOrCreateAiComponent(Document document) {
        // Look for existing component
        NodeList componentList = document.getElementsByTagName("component");
        for (int i = 0; i < componentList.getLength(); i++) {
            Node componentNode = componentList.item(i);
            if (componentNode.getNodeType() == Node.ELEMENT_NODE) {
                Element componentElement = (Element) componentNode;
                String componentName = componentElement.getAttribute("name");
                
                if (componentName.equals("AIAssistantCustomInstructionsStorage")) {
                    return componentElement;
                }
            }
        }
        
        // If not found, create a new component with the correct structure
        Element rootElement = document.getDocumentElement();
        Element aiComponent = document.createElement("component");
        aiComponent.setAttribute("name", "AIAssistantCustomInstructionsStorage");
        rootElement.appendChild(aiComponent);
        
        // Create the instructions option
        Element instructionsOption = document.createElement("option");
        instructionsOption.setAttribute("name", "instructions");
        aiComponent.appendChild(instructionsOption);
        
        // Create the map element
        Element mapElement = document.createElement("map");
        instructionsOption.appendChild(mapElement);
        
        return aiComponent;
    }

    /**
     * Updates the AIAssistantCustomInstructionsStorage component with instructions
     *
     * @param aiComponent    The component element to update
     * @param instructionsMap Map of action IDs to instruction content
     * @param document       The XML document
     */
    private void updateAiComponentWithInstructions(Element aiComponent, Map<String, String> instructionsMap, Document document) {
        // Find or create instructions option
        Element instructionsOption = null;
        NodeList optionNodes = aiComponent.getElementsByTagName("option");
        for (int i = 0; i < optionNodes.getLength(); i++) {
            Element optionElement = (Element) optionNodes.item(i);
            if (optionElement.getAttribute("name").equals("instructions")) {
                instructionsOption = optionElement;
                break;
            }
        }
        
        if (instructionsOption == null) {
            instructionsOption = document.createElement("option");
            instructionsOption.setAttribute("name", "instructions");
            aiComponent.appendChild(instructionsOption);
        }
        
        // Find or create map element
        Element mapElement = null;
        NodeList mapNodes = instructionsOption.getElementsByTagName("map");
        if (mapNodes.getLength() > 0) {
            mapElement = (Element) mapNodes.item(0);
        } else {
            mapElement = document.createElement("map");
            instructionsOption.appendChild(mapElement);
        }
        
        // Add or update entries
        for (Map.Entry<String, String> entry : instructionsMap.entrySet()) {
            String actionId = entry.getKey();
            String content = entry.getValue();
            
            // Check if entry already exists
            boolean entryExists = false;
            NodeList entryNodes = mapElement.getElementsByTagName("entry");
            for (int i = 0; i < entryNodes.getLength(); i++) {
                Element entryElement = (Element) entryNodes.item(i);
                if (entryElement.getAttribute("key").equals(actionId)) {
                    updateEntryContent(entryElement, content, document);
                    entryExists = true;
                    break;
                }
            }
            
            // If entry doesn't exist, create it
            if (!entryExists) {
                createNewEntry(mapElement, actionId, content, document);
            }
        }
    }

    /**
     * Updates the content of an existing entry
     *
     * @param entryElement The entry element to update
     * @param content      The new content
     * @param document     The XML document
     */
    private void updateEntryContent(Element entryElement, String content, Document document) {
        NodeList valueNodes = entryElement.getElementsByTagName("value");
        if (valueNodes.getLength() > 0) {
            Element valueElement = (Element) valueNodes.item(0);
            NodeList instructionNodes = valueElement.getElementsByTagName("AIAssistantStoredInstruction");
            
            if (instructionNodes.getLength() > 0) {
                Element instructionElement = (Element) instructionNodes.item(0);
            
            // Make sure actionId is set
            String actionId = entryElement.getAttribute("key");
            boolean hasActionId = false;
            
            // Check for actionId option
            NodeList optionNodes = instructionElement.getElementsByTagName("option");
            for (int i = 0; i < optionNodes.getLength(); i++) {
                Element optionElement = (Element) optionNodes.item(i);
                if (optionElement.getAttribute("name").equals("actionId")) {
                    hasActionId = true;
                    // Ensure it has the correct value
                    optionElement.setAttribute("value", actionId);
                } else if (optionElement.getAttribute("name").equals("content")) {
                    // Update content value
                    optionElement.setAttribute("value", content);
                }
            }
            
            // If actionId option doesn't exist, create it
            if (!hasActionId) {
                Element actionIdOption = document.createElement("option");
                actionIdOption.setAttribute("name", "actionId");
                actionIdOption.setAttribute("value", actionId);
                instructionElement.appendChild(actionIdOption);
            }
            
            // Check if content option exists, if not create it
            boolean hasContent = false;
            for (int i = 0; i < optionNodes.getLength(); i++) {
                Element optionElement = (Element) optionNodes.item(i);
                if (optionElement.getAttribute("name").equals("content")) {
                    hasContent = true;
                    break;
                }
            }
            
            if (!hasContent) {
                Element contentOption = document.createElement("option");
                contentOption.setAttribute("name", "content");
                contentOption.setAttribute("value", content);
                instructionElement.appendChild(contentOption);
            }
        }
    }
}

    /**
     * Creates a new entry in the map
     *
     * @param mapElement The map element
     * @param actionId   The action ID
     * @param content    The instruction content
     * @param document   The XML document
     */
    private void createNewEntry(Element mapElement, String actionId, String content, Document document) {
        // Create entry
        Element entryElement = document.createElement("entry");
        entryElement.setAttribute("key", actionId);
        mapElement.appendChild(entryElement);
        
        // Create value
        Element valueElement = document.createElement("value");
        entryElement.appendChild(valueElement);
        
        // Create instruction
        Element instructionElement = document.createElement("AIAssistantStoredInstruction");
        valueElement.appendChild(instructionElement);

        // Add actionId option
        Element actionIdOption = document.createElement("option");
        actionIdOption.setAttribute("name", "actionId");
        actionIdOption.setAttribute("value", actionId);
        instructionElement.appendChild(actionIdOption);

        // Add content option
        Element contentOption = document.createElement("option");
        contentOption.setAttribute("name", "content");
        contentOption.setAttribute("value", content);
        instructionElement.appendChild(contentOption);
    }

    @Override
    public void update(AnActionEvent e) {
        // Set the availability based on whether a project is open
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}