package net.wouterdanes.docker.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.wouterdanes.docker.maven.ContainerStartConfiguration;
import net.wouterdanes.docker.maven.ExposedPort;
import net.wouterdanes.docker.remoteapi.ContainerCreateRequest;
import net.wouterdanes.docker.remoteapi.ContainerInspectionResult;
import net.wouterdanes.docker.remoteapi.ContainerStartRequest;
import net.wouterdanes.docker.remoteapi.ContainersService;
import net.wouterdanes.docker.remoteapi.ImagesService;
import net.wouterdanes.docker.remoteapi.exception.ImageNotFoundException;
import net.wouterdanes.docker.remoteapi.util.DockerHostFromEnvironmentSupplier;
import net.wouterdanes.docker.remoteapi.util.DockerHostFromPropertySupplier;
import net.wouterdanes.docker.remoteapi.util.DockerPortFromEnvironmentSupplier;
import net.wouterdanes.docker.remoteapi.util.DockerPortFromPropertySupplier;

/**
 * This class is responsible for providing a docker interface with a remote (not running on localhost) docker host. It
 * can be configured by setting an environment variable {@value #DOCKER_HOST_SYSTEM_ENV }, like in the client. Or you
 * can specify the host and port on the command line like such:
 * <pre>-D{@value #DOCKER_HOST_PROPERTY}=[host] -D{@value #DOCKER_PORT_PROPERTY}=[port]</pre>
 * <p/>
 * The provider defaults to {@value #TCP_PROTOCOL}://{@value #DEFAULT_DOCKER_HOST}:{@value #DEFAULT_DOCKER_PORT}
 */
public class RemoteDockerProvider implements DockerProvider {

    public static final String DOCKER_HOST_SYSTEM_ENV = "DOCKER_HOST";
    public static final String DOCKER_HOST_PROPERTY = "docker.host";
    public static final String DOCKER_PORT_PROPERTY = "docker.port";

    public static final String TCP_PROTOCOL = "tcp";
    public static final int DEFAULT_DOCKER_PORT = 4243;
    public static final String DEFAULT_DOCKER_HOST = "127.0.0.1";

    private final String host;
    private final int port;

    private final ContainersService containersService;
    private final ImagesService imagesService;

    public RemoteDockerProvider() {
        this(getDockerHostFromEnvironment(), getDockerPortFromEnvironment());
    }

    public RemoteDockerProvider(final String host, final int port) {
        this.host = host;
        this.port = port;

        String dockerApiRoot = String.format("http://%s:%s", host, port);
        containersService = new ContainersService(dockerApiRoot);
        imagesService = new ImagesService(dockerApiRoot);
    }

    @Override
    public String startContainer(final ContainerStartConfiguration configuration) {
        String imageId = configuration.getImage();
        ContainerCreateRequest createRequest = new ContainerCreateRequest()
                .fromImage(imageId);

        String containerId;
        try {
            containerId = containersService.createContainer(createRequest);
        } catch (ImageNotFoundException e) {
            imagesService.pullImage(imageId);
            containerId = containersService.createContainer(createRequest);
        }
        ContainerStartRequest containerStartRequest = new ContainerStartRequest()
                .withAllPortsPublished();

        containersService.startContainer(containerId, containerStartRequest);

        return containerId;
    }

    @Override
    public void stopContainer(final String containerId) {
        containersService.killContainer(containerId);
    }

    @Override
    public void deleteContainer(final String containerId) {
        containersService.deleteContainer(containerId);
    }

    @Override
    public List<ExposedPort> getExposedPorts(final String containerId) {
        ContainerInspectionResult containerInspectionResult = containersService.inspectContainer(containerId);
        if (containerInspectionResult.getNetworkSettings().getPorts().isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<ContainerInspectionResult.NetworkSettings.PortMappingInfo>> ports =
                containerInspectionResult.getNetworkSettings().getPorts();
        List<ExposedPort> exposedPorts = new ArrayList<>();
        for (Map.Entry<String, List<ContainerInspectionResult.NetworkSettings.PortMappingInfo>> port : ports.entrySet()) {
            String exposedPort = port.getKey();
            int hostPort = port.getValue().get(0).getHostPort();
            exposedPorts.add(new ExposedPort(exposedPort, hostPort, host));
        }
        return exposedPorts;
    }

    @Override
    public String toString() {
        return "RemoteDockerProvider{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }

    private static Integer getDockerPortFromEnvironment() {
        return DockerPortFromPropertySupplier.INSTANCE.get()
                .or(DockerPortFromEnvironmentSupplier.INSTANCE.get())
                .or(DEFAULT_DOCKER_PORT);
    }

    private static String getDockerHostFromEnvironment() {
        return DockerHostFromPropertySupplier.INSTANCE.get()
                .or(DockerHostFromEnvironmentSupplier.INSTANCE.get())
                .or(DEFAULT_DOCKER_HOST);
    }

}
