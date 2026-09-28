package com.bosc.agentops.observability.dto;

/** 每日调用趋势项。failed = total - success（含 FAILED 与 REJECTED）。 */
public class DailyTrendItem {

    /** yyyy-MM-dd */
    private String date;
    private long total;
    private long success;
    private long failed;

    public DailyTrendItem() {
    }

    public DailyTrendItem(String date, long total, long success, long failed) {
        this.date = date;
        this.total = total;
        this.success = success;
        this.failed = failed;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getSuccess() {
        return success;
    }

    public void setSuccess(long success) {
        this.success = success;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }
}
