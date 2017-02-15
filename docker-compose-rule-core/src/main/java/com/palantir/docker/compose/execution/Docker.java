/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.docker.compose.execution;

import static com.google.common.base.Preconditions.checkState;

import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ObjectArrays;
import com.palantir.docker.compose.connection.DockerMachine;
import com.palantir.docker.compose.connection.State;
import java.io.IOException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Docker {

    private static final Logger log = LoggerFactory.getLogger(Docker.class);

    private static final Pattern VERSION_PATTERN = Pattern.compile("Docker version (\\d+\\.\\d+\\.\\d+).*");
    private static final String HEALTH_STATUS_FORMAT =
            "--format="
                    + "{{if not .State.Running}}DOWN"
                    + "{{else if .State.Paused}}PAUSED"
                    + "{{else if index .State \"Health\"}}"
                    + "{{if eq .State.Health.Status \"healthy\"}}HEALTHY"
                    + "{{else}}UNHEALTHY{{end}}"
                    + "{{else}}HEALTHY{{end}}";

    public static Version version() throws IOException, InterruptedException {
        Command command = new Command(
                DockerExecutable.builder().dockerConfiguration(DockerMachine.localMachine().build()).build(), log::trace);
        String versionString = command.execute(Command.throwingOnError(), "-v");
        Matcher matcher = VERSION_PATTERN.matcher(versionString);
        checkState(matcher.matches(), "Unexpected output of docker -v: %s", versionString);
        return Version.valueOf(matcher.group(1));
    }

    private final Command command;

    public Docker(DockerExecutable rawExecutable) {
        this.command = new Command(rawExecutable, log::trace);
    }

    public State state(String containerId) throws IOException, InterruptedException {
        String stateString = command.execute(
                Command.throwingOnError(), "inspect", HEALTH_STATUS_FORMAT, containerId);
        return State.valueOf(stateString);
    }

    public void rm(Collection<String> containerNames) throws IOException, InterruptedException {
        rm(containerNames.toArray(new String[containerNames.size()]));
    }

    public void rm(String... containerNames) throws IOException, InterruptedException {
        command.execute(Command.throwingOnError(),
                ObjectArrays.concat(new String[] {"rm", "-f"}, containerNames, String.class));
    }

    public String listNetworks() throws IOException, InterruptedException {
        return command.execute(Command.throwingOnError(), "network", "ls");
    }
}
