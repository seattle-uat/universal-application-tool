package services.applicant;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/** Provides synchronous, read-only behavior relevant to an applicant for a specific program. */
public interface ReadOnlyApplicantProgramService {
  /** Returns the program title, localized to the applicant's preferred locale. */
  String getProgramTitle();

  /**
   * Get the {@link Block}s for this program and applicant. This includes all blocks, whether the
   * block was filled out in this program or a previous program.
   */
  ImmutableList<Block> getAllBlocks();

  /**
   * Get the {@link Block}s this applicant needs to fill out or has filled out for this program.
   *
   * <p>This list includes any block that is incomplete or has errors (which indicate the applicant
   * needs to make a correction), or any block that was completed while filling out this program
   * form.
   *
   * <p>This list does not include blocks that were completely filled out in a different program.
   *
   * @return a list of {@link Block}s that were completed by the applicant in this session or still
   *     need to be completed for this program
   */
  ImmutableList<Block> getInProgressBlocks();

  /** Get the block with the given block ID */
  Optional<Block> getBlock(String blockId);

  /**
   * Get the next in-progress block that comes after the block with the given ID if there is one.
   */
  Optional<Block> getInProgressBlockAfter(String blockId);

  /** Return the index of the given block in the context of all blocks of the program. */
  int getBlockIndex(String blockId);

  /** Get the program block with the lowest index that has missing answer data if there is one. */
  Optional<Block> getFirstIncompleteBlock();

  /**
   * Return a list of answer data for each question in this application ordered by block and
   * question index.
   */
  ImmutableList<AnswerData> getSummaryData();

  /**
   * Return a map of answer data for each question in this application keyed by identifiers
   * generated from `answerDataKey()`.
   */
  ImmutableMap<String, AnswerData> getSummaryDataMap();

  /**
   * Return a string that uniquely identifies an {@link AnswerData} within an applicant program and
   * is shared across applicant programs.
   */
  String answerDataKey(AnswerData answerData);

  /**
   * Return true if this program fully supports this applicant's preferred language, and false
   * otherwise.
   */
  boolean preferredLanguageSupported();
}
