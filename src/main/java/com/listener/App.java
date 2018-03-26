package com.listener;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import com.jcabi.log.VerboseProcess;

import fi.iki.elonen.NanoHTTPD;


public class App extends NanoHTTPD {

    static String REPOS_PATH = Optional.ofNullable(System.getenv("REPOS_PATH")).orElse("/tmp/temp_git_repos/");
    static String HTTP_PORT = Optional.ofNullable(System.getenv("HTTP_PORT")).orElse("");
    static String GITHUB_TOKEN = Optional.ofNullable(System.getenv("GITHUB_TOKEN")).orElse("");
    static String GITLAB_URL = Optional.ofNullable(System.getenv("GITLAB_URL")).orElse("");
    static String GITLAB_TOKEN = Optional.ofNullable(System.getenv("GITLAB_TOKEN")).orElse("");
    static String SONAR_URL = Optional.ofNullable(System.getenv("SONAR_URL")).orElse("");
    static String SONAR_TOKEN = Optional.ofNullable(System.getenv("SONAR_TOKEN")).orElse("");
    static String ANALYZE_TARGET = Optional.ofNullable(System.getenv("ANALYZE_TARGET")).orElse("");
    static String RUN_ONLY_ON_EVENTS = Optional.ofNullable(System.getenv("RUN_ONLY_ON_EVENTS")).orElse("");

    public App() throws IOException, NumberFormatException {
        super(Integer.parseInt(HTTP_PORT));
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println(String.format("\nRunning! Point your browsers to http://0.0.0.0:%s/ \n", HTTP_PORT));
    }

    public static void main(String[] args) {
        try {
            new App();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {

        Method method = session.getMethod();
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
            try {
                final HashMap<String, String> map = new HashMap<String, String>();
                session.parseBody(map);
                final String json = map.get("postData");
                System.out.println("\nRequest \n uri: " + session.getUri() + "\nbody: " + json);
                Runnable r = () -> runSonarAnalysis(json);
                new Thread(r).start();
            } catch (IOException | ResponseException e) {
                System.err.println("Request Error:\n" + e);
                return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", "Error!");
            }
            return newFixedLengthResponse("OK"); // Or postParameter.
        }
        return newFixedLengthResponse("I ok, I wait.");
    }

    private void runSonarAnalysis(String postData) {

        String gitRepoName = "";
        String gitRepoUrl = "";
        String headCommit = "";
        String gitBranchName = "";
        String gitBaseBranchName = "";
        String requestEvent = "";
        int gitProjectId = 0;
        int pullRequest = 0;
        boolean isGithub = false;
        boolean isGitlab = false;
        try {
            // github
            DocumentContext jsonContext = JsonPath.parse(postData);

            gitRepoName = jsonContext.read("$['pull_request']['head']['repo']['full_name']");
            gitBranchName = jsonContext.read("$['pull_request']['head']['ref']");
            headCommit = jsonContext.read("$['pull_request']['head']['sha']");
            gitBaseBranchName = jsonContext.read("$['pull_request']['base']['ref']");
            requestEvent = jsonContext.read("$['action']");
            pullRequest = jsonContext.read("$['pull_request']['number']");
            gitRepoUrl = jsonContext.read("$['pull_request']['head']['repo']['clone_url']");
            gitRepoUrl = gitRepoUrl.replace("://", "://" + GITHUB_TOKEN + "@");
            isGithub = true;
        } catch (PathNotFoundException e) {
            System.out.println("Path not found ( not a github pull request )");
        }

        if (!isGithub) {
            try {
                // gitlab
                DocumentContext jsonContext = JsonPath.parse(postData);

                gitBranchName = jsonContext.read("$['object_attributes']['source_branch']");
                headCommit = jsonContext.read("$['object_attributes']['last_commit']['id']");
                gitBaseBranchName = jsonContext.read("$['object_attributes']['target_branch']");
                requestEvent = jsonContext.read("$['object_attributes']['action']");
                gitRepoName = jsonContext.read("$['object_attributes']['source']['path_with_namespace']");
                gitProjectId = jsonContext.read("$['project']['id']");
                gitRepoUrl = jsonContext.read("$['object_attributes']['source']['git_http_url']");
                gitRepoUrl = gitRepoUrl.replace("://", "://gitlab-ci-token:" + GITLAB_TOKEN + "@");
                isGitlab = true;
            } catch (PathNotFoundException e) {
                System.out.println("Path not found ( not a gitlab pull request )");
            }
        }

        if (!isGithub && !isGitlab) {
            System.out.println("Skip sonar scan trigger");
            return;
        }
        if (!shouldRunOnThisEvent(requestEvent)) {
            System.out.println("Skipping this request, as its `" + requestEvent + "` only allowed to run `RUN_ONLY_ON_EVENTS` -- " + RUN_ONLY_ON_EVENTS);
            return;
        }
        if (shouldRunSourceScan(gitBaseBranchName)) {
            System.out.println("Analyzing Source branch");
            runScanner(gitRepoName, "", gitBaseBranchName, gitRepoUrl, 0, gitProjectId, isGithub, "publish");
        }
        System.out.println("Analyzing pull/merge request");
        runScanner(gitRepoName, headCommit, gitBranchName, gitRepoUrl, pullRequest, gitProjectId, isGithub, "preview");

    }

    private boolean shouldRunOnThisEvent(String eventName) {
        if (RUN_ONLY_ON_EVENTS.equals("")) {
            return true;
        }
        return Arrays.asList(RUN_ONLY_ON_EVENTS.split(",")).contains(eventName);
    }

    private boolean shouldRunSourceScan(String sourceBranchName) {
        if (ANALYZE_TARGET.equals("")) {
            return true;
        }
        return Arrays.asList(ANALYZE_TARGET.split(",")).contains(sourceBranchName);
    }

    private void runScanner(String gitRepoName, String headCommit, String gitBranchName, String gitRepoUrl, int pullRequest, int gitProjectId, boolean isGithub, String mode) {
        String localPath = REPOS_PATH + gitRepoName + "/" + headCommit;

        try {
            runCommand("mkdir -p " + localPath);
            runCommand("git clone --depth 1 -b " + gitBranchName + " --single-branch " + gitRepoUrl + " .", localPath);
            String scannerCommand = "/usr/src/sonar-scanner/sonar-scanner " +
                    " -Dproject.home=." +
                    " -Dsonar.analysis.mode=" + mode +
                    " -Dsonar.host.url=" + SONAR_URL +
                    " -Dsonar.issuesReport.console.enable=true" +
                    " -Dsonar.login=" + SONAR_TOKEN;

            if (mode.equals("preview")) {
                if (isGithub) {
                    scannerCommand = scannerCommand +
                            " -Dsonar.github.pullRequest=" + pullRequest +
                            " -Dsonar.github.repository=" + gitRepoName +
                            " -Dsonar.github.oauth=" + GITHUB_TOKEN;
                } else {
                    scannerCommand = scannerCommand +
                            " -Dsonar.gitlab.failure_notification_mode=commit-status" +
                            " -Dsonar.gitlab.json_mode=CODECLIMATE" +
                            (headCommit.equals("") ? "" : " -Dsonar.gitlab.commit_sha=" + headCommit) +
                            " -Dsonar.gitlab.project_id=" + gitProjectId +
                            " -Dsonar.gitlab.ref_name=" + gitBranchName +
                            (GITLAB_TOKEN.equals("") ? "" : " -Dsonar.gitlab.user_token=" + GITLAB_TOKEN) +
                            (GITLAB_URL.equals("") ? "" : " -Dsonar.gitlab.url=" + GITLAB_URL);
                }
            }
            runCommand(scannerCommand, localPath);
            if (!localPath.equals("/")) {
                runCommand("rm -rf " + localPath);
            }
            System.out.println("Done, bring me another!");
        } catch (IOException | InterruptedException e) {
            System.out.println("Exception happened - here's what I know: " + e);
            e.printStackTrace();
        }
    }

    private void runCommand(String command) throws IOException, InterruptedException {
        runCommand(command, "");
    }

    private void runCommand(String command, String workingDirectory) throws IOException, InterruptedException {
        System.out.println("\n$ " + command + "\n");
        Process p;
        if (workingDirectory.equals("")) {
            p = Runtime.getRuntime().exec(command);
        } else {
            p = Runtime.getRuntime().exec(command, null, new File(workingDirectory));
        }
        VerboseProcess.Result vpr = new VerboseProcess(p).waitFor();
        System.out.println(vpr.stdout() + "\n" + vpr.stderr() + "\n--\n");
    }
}