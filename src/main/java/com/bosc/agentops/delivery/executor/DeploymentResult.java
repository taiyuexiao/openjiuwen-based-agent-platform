package com.bosc.agentops.delivery.executor;

/**
 * 执行器部署结果。
 */
public class DeploymentResult {

    private final boolean success;
    private final String instanceUrl;
    private final String message;

    private DeploymentResult(boolean success, String instanceUrl, String message) {
        this.success = success;
        this.instanceUrl = instanceUrl;
        this.message = message;
    }

    public static DeploymentResult success(String instanceUrl) {
        return new DeploymentResult(true, instanceUrl, null);
    }

    public static DeploymentResult failure(String message) {
        return new DeploymentResult(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public String getMessage() {
        return message;
    }
}
