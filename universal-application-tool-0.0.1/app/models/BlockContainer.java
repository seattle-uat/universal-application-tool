package models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import services.program.BlockDefinition;

/**
 * Used only for serializing a {@link services.program.ProgramDefinition}'s list of blocks to the
 * database.
 */
public class BlockContainer {

  private final List<BlockDefinition> blockDefinitions;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public BlockContainer(@JsonProperty("blockDefinitions") List<BlockDefinition> blockDefinitions) {
    this.blockDefinitions = blockDefinitions;
  }

  public List<BlockDefinition> blockDefinitions() {
    return this.blockDefinitions;
  }
}
