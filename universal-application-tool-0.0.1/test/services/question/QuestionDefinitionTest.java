package services.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Locale;

public class QuestionDefinitionTest {

  @Test
  public void newQuestionHasCorrectFields() {
    QuestionDefinition question =
        new QuestionDefinition(
            "id",
            "version",
            "name",
            "my.path",
            "description",
            ImmutableMap.of(Locale.ENGLISH, "question?"),
            ImmutableMap.of(Locale.ENGLISH, "help text"),
            ImmutableSet.of("general"));
    assertThat(question.getId()).isEqualTo("id");
    assertThat(question.getVersion()).isEqualTo("version");
    assertThat(question.getName()).isEqualTo("name");
    assertThat(question.getPath()).isEqualTo("my.path");
    assertThat(question.getDescription()).isEqualTo("description");
    assertThat(question.getQuestionText())
        .containsAllEntriesOf(ImmutableMap.of(Locale.ENGLISH, "question?"));
    assertThat(question.getQuestionHelpText())
        .containsAllEntriesOf(ImmutableMap.of(Locale.ENGLISH, "help text"));
    assertThat(question.getTags()).containsExactly("general");
  }

  @Test
  public void newQuestionHasTypeText() {
    QuestionDefinition question =
        new QuestionDefinition(
            "", "", "", "", "", ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of());
    assertThat(question.getQuestionType()).isEqualTo(QuestionType.TEXT);
  }

  @Test
  public void newQuestionHasTextScalar() {
    QuestionDefinition question =
        new QuestionDefinition(
            "", "", "", "", "", ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of());
    assertThat(question.getScalars()).containsExactly("text");
  }
}
