package com.bosc.agentops.agentdev.dto;

/** Agent 广场查询：keyword 对 code/name/description 模糊过滤，可空。 */
public class AgentSquareListReq {

    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
