# trigger-sonar-scanner
Github/Gitlab webhook listener to fire sonar-scanner

https://hub.docker.com/r/hasnat/trigger-sonar-scanner/

### Running the api
```
docker run -it -d \
    --name trigger-sonar-scanner \
    -p 8000:8080 \
    -e HTTP_PORT=8080 \
    -e SONAR_TOKEN=XXXXX \
    -e SONAR_URL=https://sonar.website \
    -e GITHUB_TOKEN=YYYYY \
    -e GITLAB_TOKEN=YYYY \
    -e GITLAB_URL=https://gitlab.website \
    -e ANALYZE_TARGET=master,develop \
    -e RUN_ONLY_ON_EVENTS=open,reopen,update,opened,reopened,synchronize \
    -e 'SONAR_SCANNER_DEBUG_OPTS=-Dsonar.verbose=true' \
    hasnat/trigger-sonar-scanner

```

Setup webhooks for github or gitlab

Features
--------
- pull requests code review for github
- requests code review for gitlab


Available environment variables
-------------------------------
- `GITHUB_TOKEN`             with [repo, write:discussion] permissions ( https://github.com/settings/tokens ) ( `-Dsonar.github.oauth` )
- `GITLAB_TOKEN`             with api, read permissions - required for cloning repo ( token can be set in Sonar admin gitlab plugin setting but still required here ) ( `-Dsonar.gitlab.user_token` )
- `GITLAB_URL`               your gitlab url, ( can be set in Sonar admin gitlab plugin setting ) ( `-Dsonar.gitlab.url` )
- `SONAR_URL`                sonar URL - required ( `-Dsonar.host.url` )
- `SONAR_TOKEN`              sonar login token - required ( `-Dsonar.login` )
- `REPOS_PATH`               path where App will temporarily clone repo - default `/tmp/temp_git_repos/`
- `HTTP_PORT`                port for api to run - default 8080
- `ANALYZE_TARGET`           allowed branches to run sonar before analyzing pull/merge request - default `''` ( runs on all pull request target branches helpful if you want to keep master in sync ) to disable use some weird branch name e.g. [`this_branch_wont_be_sonared`]
- `RUN_ONLY_ON_EVENTS`       run only on specific github/gitlab events - default `''` ( run scanner on all events )
- `SONAR_SCANNER_OPTS`       any sonar options
- `SONAR_SCANNER_DEBUG_OPTS` any sonar debug options

If no gitlab or github tokens provided or set in sonar

### Development

Clone this repo
#### Run local Sonar
```
docker run -d --name sonarqube -p 9000:9000 -p 9092:9092 sonarqube
```

#### Build and run local Sonar trigger API
```
docker build -t trigger-sonar-scanner-api .

docker run -it --rm \
    --name trigger-sonar-scanner \
    -p 8000:8080 \
    -e HTTP_PORT=8080 \
    -e SONAR_TOKEN=XXXXX \
    -e GITLAB_URL=https://gitlab.website \
    -e SONAR_URL=http://docker.for.mac.localhost:9000 \
    -e GITLAB_TOKEN=YYYY \
    -e GITHUB_TOKEN=YYYYY \
    -e ANALYZE_TARGET=master,develop \
    -e RUN_ONLY_ON_EVENTS=open,reopen,update,opened,reopened,synchronize \
    -e 'SONAR_SCANNER_DEBUG_OPTS=-Dsonar.verbose=true' \
    trigger-sonar-scanner-api

ngrok http 8000
```

## References
- https://sonarsource.bintray.com/Distribution/sonar-scanner-cli/
- https://developer.github.com/v3/activity/events/types/#pullrequestevent
- https://docs.gitlab.com/ee/user/project/integrations/webhooks.html#merge-request-events
- https://docs.sonarqube.org/display/PLUG/GitHub+Plugin
- https://github.com/gabrie-allaigre/sonar-gitlab-plugin
- https://ngrok.com/
