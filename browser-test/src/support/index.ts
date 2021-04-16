import { Browser, chromium, Page } from 'playwright'
export { AdminQuestions } from './admin_questions'
export { AdminPrograms } from './admin_programs'
export { AdminTranslations } from './admin_translations'
export { AdminTIGroups } from './admin_ti_groups'
export { ApplicantQuestions } from './applicant_questions'

export const { BASE_URL = 'http://civiform:9000', TEST_USER_LOGIN = '', TEST_USER_PASSWORD = '' } = process.env

export const startSession = async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ acceptDownloads: true });

  await page.goto(BASE_URL);

  return { browser, page };
}

export const endSession = async (browser: Browser) => {
  await browser.close();
}

export const gotoRootUrl = async (page: Page) => {
  await page.goto(BASE_URL);
}

export const gotoEndpoint = async (page: Page, endpoint: string) => {
  return await page.goto(BASE_URL + endpoint);
}

export const logout = async (page: Page) => {
  await page.click('text=Logout');
}

export const loginAsAdmin = async (page: Page) => {
  await page.click('#admin');
}

export const loginAsProgramAdmin = async (page: Page) => {
  await page.click('#program-admin');
}

export const loginAsGuest = async (page: Page) => {
  await page.click('#guest');
}

export const loginAsTestUser = async (page: Page) => {
  if (isTestUser()) {
    await page.click("#idcs");
    await page.fill("#idcs-signin-basic-signin-form-username", TEST_USER_LOGIN);
    await page.fill("#idcs-signin-basic-signin-form-password > input", TEST_USER_PASSWORD);
    await page.click("#idcs-signin-basic-signin-form-submit")
  } else {
    await page.click('#guest');
  }
}

function isTestUser() {
  return TEST_USER_LOGIN !== '' && TEST_USER_PASSWORD !== ''
}


export const userDisplayName = () => {
  if (isTestUser()) {
    return 'TEST, UATAPP'
  } else {
    return '<Anonymous Applicant>'
  }
}

/**
 * The option to select a language is only shown once for a given applicant. If this is
 * the first time they see this page, select the given language. Otherwise continue.
 */
export const selectApplicantLanguage = async (page: Page, language: string) => {
  const infoPageRegex = /applicants\/\d+\/edit/;
  const maybeSelectLanguagePage = await page.url();
  if (maybeSelectLanguagePage.match(infoPageRegex)) {
    const languageOption = `.cf-radio-option:has-text("${language}")`;
    await page.click(languageOption + ' input');
    await page.click('button:visible');
  }

  const programIndexRegex = /applicants\/\d+\/programs/;
  const maybeProgramIndexPage = await page.url();
  expect(maybeProgramIndexPage).toMatch(programIndexRegex);
}

export const loginWithSimulatedIdcs = async (page: Page) => {
  await page.click('#idcs');

  let pg_source = await page.content();

  if (pg_source.includes("Enter any login")) {
    await page.click('css=[name=login]');
    await page.keyboard.type('username');
    await page.click('css=[name=password]');
    await page.keyboard.type('password');

    console.log(pg_source);

    await page.click('.login-submit');
  }

  await page.click('.login-submit');
}

export const dropTables = async (page: Page) => {
  await page.goto(BASE_URL + '/dev/seed');
  await page.click("#clear");
}
