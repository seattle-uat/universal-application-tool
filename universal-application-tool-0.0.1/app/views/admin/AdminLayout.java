package views.admin;

import static j2html.TagCreator.a;
import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.head;
import static j2html.TagCreator.main;
import static j2html.TagCreator.nav;
import static j2html.TagCreator.span;
import static j2html.TagCreator.text;

import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import j2html.tags.Tag;
import javax.inject.Inject;
import play.twirl.api.Content;
import views.BaseHtmlLayout;
import views.ViewUtils;
import views.style.BaseStyles;
import views.style.StyleUtils;
import views.style.Styles;

public class AdminLayout extends BaseHtmlLayout {

  @Inject
  public AdminLayout(ViewUtils viewUtils) {
    super(viewUtils);
  }

  private ContainerTag renderNavBar() {
    String questionLink = controllers.admin.routes.QuestionController.index().url();
    String programLink = controllers.admin.routes.AdminProgramController.index().url();
    String logoutLink = org.pac4j.play.routes.LogoutController.logout().url();

    ContainerTag headerIcon =
        div(span("C"), span("F").withClasses(Styles.FONT_THIN))
            .withClasses(
                Styles.ABSOLUTE,
                Styles.BG_CONTAIN,
                Styles.BG_GRAY_700,
                Styles.H_7,
                Styles.LEFT_5,
                Styles.M_1,
                Styles.OPACITY_75,
                Styles.TEXT_CENTER,
                Styles.TEXT_LG,
                Styles.TEXT_WHITE,
                Styles.TOP_2,
                Styles.W_7,
                Styles.ROUNDED);
    ContainerTag headerTitle =
        div()
            .withClasses(
                Styles.FONT_NORMAL, Styles.INLINE, Styles.PL_10, Styles.PY_0, Styles.TEXT_XL)
            .with(span("Civi"), span("Form").withClasses(Styles.FONT_THIN));

    ContainerTag adminHeader =
        nav()
            .with(headerIcon, headerTitle)
            .with(headerLink("Questions", questionLink))
            .with(headerLink("Programs", programLink))
            .with(headerLink("Logout", logoutLink, Styles.FLOAT_RIGHT))
            .withClasses(BaseStyles.NAV_STYLES);
    return adminHeader;
  }

  public Content renderMain(ContainerTag mainContent, String... mainStyles) {
    mainContent.withClasses(
        Styles.BG_WHITE,
        Styles.BORDER,
        Styles.BORDER_GRAY_200,
        Styles.PX_2,
        Styles.SHADOW_LG,
        StyleUtils.joinStyles(mainStyles));

    return htmlContent(
        head(tailwindStyles()),
        body()
            .with(renderNavBar())
            .with(mainContent)
            .withClasses(
                BaseStyles.BODY_GRADIENT_STYLE,
                Styles.BOX_BORDER,
                Styles.H_SCREEN,
                Styles.W_SCREEN,
                Styles.OVERFLOW_HIDDEN,
                Styles.FLEX));
  }

  /** Renders mainDomContents within the main tag, in the context of the admin layout. */
  public Content render(DomContent... mainDomContents) {
    return renderMain(main(mainDomContents));
  }

  public Tag headerLink(String text, String href, String... styles) {
    return a(text)
        .withHref(href)
        .withClasses(
            Styles.PX_3,
            Styles.OPACITY_75,
            StyleUtils.hover(Styles.OPACITY_100),
            StyleUtils.joinStyles(styles));
  }
}
