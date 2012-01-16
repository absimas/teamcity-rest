package jetbrains.buildServer.server.rest.model.buildType;

import javax.xml.bind.annotation.XmlRootElement;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptor;
import jetbrains.buildServer.buildTriggers.BuildTriggerDescriptorFactory;
import jetbrains.buildServer.server.rest.errors.OperationException;
import jetbrains.buildServer.serverSide.BuildTypeSettings;

/**
 * @author Yegor.Yarko
 *         Date: 05.01.12
 */
@XmlRootElement(name = "trigger")
public class PropEntityTrigger extends PropEntity {

  public PropEntityTrigger() {
  }

  public PropEntityTrigger(final BuildTriggerDescriptor descriptor, final BuildTypeSettings buildTypeSettings) {
    super(descriptor, buildTypeSettings);
  }

  public BuildTriggerDescriptor addTrigger(final BuildTypeSettings buildType, final BuildTriggerDescriptorFactory descriptorFactory) {
    final BuildTriggerDescriptor triggerToAdd = descriptorFactory.createTriggerDescriptor(type, properties.getMap());

    if (!buildType.addBuildTrigger(triggerToAdd)) {
      String additionalMessage = getDetails(buildType, triggerToAdd);
      throw new OperationException("Build trigger addition failed." + (additionalMessage != null ? " " + additionalMessage : ""));
    }
    if (disabled != null) {
      buildType.setEnabled(triggerToAdd.getId(), !disabled);
    }
    return buildType.findTriggerById(triggerToAdd.getId());
  }

  private String getDetails(final BuildTypeSettings buildType, final BuildTriggerDescriptor triggerToAdd) {
    final BuildTriggerDescriptor foundTriggerWithSameId = buildType.findTriggerById(triggerToAdd.getId());
    if (foundTriggerWithSameId != null) {
      return "Trigger with id '" + triggerToAdd.getId() + "'already exists.";
    }
    return null;
  }
}
