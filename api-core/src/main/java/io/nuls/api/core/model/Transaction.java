package io.nuls.api.core.model;

import java.util.List;

public class Transaction<T extends TxData> {

    private String hash;

    private Integer type;

    private Long height;

    private String agentId;

    private String agentAddress;

    private Integer size;

    private Long fee;

    private String remark;

    private String txDataHex;

    private T txData;

    private List<Input> froms;

    private List<OutPut> tos;

    private Long values;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentAddress() {
        return agentAddress;
    }

    public void setAgentAddress(String agentAddress) {
        this.agentAddress = agentAddress;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getFee() {
        return fee;
    }

    public void setFee(Long fee) {
        this.fee = fee;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTxDataHex() {
        return txDataHex;
    }

    public void setTxDataHex(String txDataHex) {
        this.txDataHex = txDataHex;
    }

    public T getTxData() {
        return txData;
    }

    public void setTxData(T txData) {
        this.txData = txData;
    }

    public List<Input> getFroms() {
        return froms;
    }

    public void setFroms(List<Input> froms) {
        this.froms = froms;
    }

    public List<OutPut> getTos() {
        return tos;
    }

    public void setTos(List<OutPut> tos) {
        this.tos = tos;
    }

    public Long getValues() {
        return values;
    }

    public void setValues(Long values) {
        this.values = values;
    }
}
