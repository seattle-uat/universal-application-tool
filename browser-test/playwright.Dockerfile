FROM mcr.microsoft.com/playwright:focal

ENV PROJECT_DIR /usr/src/civiform-browser-tests

RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client-12

COPY . ${PROJECT_DIR}
RUN cd ${PROJECT_DIR} && yarn install

WORKDIR $PROJECT_DIR
