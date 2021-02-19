package views.admin;

import static j2html.TagCreator.a;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.form;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.join;

import com.google.common.collect.ImmutableList;
import j2html.tags.Tag;
import play.twirl.api.Content;
import services.program.ProgramDefinition;
import views.BaseHtmlView;

public final class ProgramIndexView extends BaseHtmlView {

  public Content render(ImmutableList<ProgramDefinition> programs) {
    return htmlContent(
        body(
            h1("Programs"),
            div(
                form(
                    a("Add a Program")
                        .attr("href", controllers.admin.routes.AdminProgramController.newOne()))),
            div(each(programs, this::shortProgram))));
  }

  public Tag shortProgram(ProgramDefinition program) {
    return div(
        div(
            join(
                program.name(),
                "(",
                a("edit")
                    .attr(
                        "href", controllers.admin.routes.AdminProgramController.edit(program.id())),
                ")")),
        div(program.description()));
  }
}
