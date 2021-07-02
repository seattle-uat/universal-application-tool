package services.question.exceptions;

/** InvalidUpdateException is thown when update to a question configuration cannot be processed. */
public class InvalidUpdateException extends Exception {
  public InvalidUpdateException(String reason) {
    super("Question update failed: " + reason);
  }
}
