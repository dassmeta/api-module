package io.nuls.api.service;

import io.nuls.api.bean.annotation.Autowired;
import io.nuls.api.bean.annotation.Component;
import io.nuls.api.bridge.WalletRPCHandler;
import io.nuls.api.core.constant.NulsConstant;
import io.nuls.api.core.model.*;
import io.nuls.api.core.mongodb.MongoDBService;
import io.nuls.api.utils.RoundManager;
import io.nuls.sdk.core.contast.TransactionConstant;
import io.nuls.sdk.core.utils.JSONUtils;
import io.nuls.sdk.core.utils.StringUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RollbackService {
    @Autowired
    private WalletRPCHandler rpcHandler;
    @Autowired
    private MongoDBService mongoDBService;
    @Autowired
    private AgentService agentService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AliasService aliasService;
    @Autowired
    private DepositService depositService;
    @Autowired
    private BlockHeaderService blockHeaderService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private UTXOService utxoService;
    @Autowired
    private PunishService punishService;
    @Autowired
    private RoundManager roundManager;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private ContractService contractService;

    //记录每个区块打包交易的所有已花费(input)
    private List<Input> inputList = new ArrayList<>();
    //记录每个区块打包交易的所有新增未花费(output)
    private Map<String, Output> outputMap = new HashMap<>();
    //记录每个区块打包交易涉及到的账户的余额变动
    private Map<String, AccountInfo> accountInfoMap = new HashMap<>();
    //记录每个区块设置别名信息
    private List<AliasInfo> aliasInfoList = new ArrayList<>();
    //记录每个区块代理节点的变化
    private List<AgentInfo> agentInfoList = new ArrayList<>();
    //记录每个区块委托共识的信息
    private List<DepositInfo> depositInfoList = new ArrayList<>();

    //记录每个区块新创建的智能合约信息
    private Map<String, ContractInfo> contractInfoMap = new HashMap<>();
    //记录智能合约的执行结果信息
    private List<ContractResultInfo> contractResultList = new ArrayList<>();
    //记录每个区块智能合约相关的账户token信息
    private Map<String, AccountTokenInfo> accountTokenMap = new HashMap<>();
    //记录智能合约相关的交易信息
    private List<ContractTxInfo> contractTxInfoList = new ArrayList<>();

    private List<String> punishTxHashList = new ArrayList<>();

    private List<String> contractTxHashList = new ArrayList<>();

    private List<String> tokenTransferHashList = new ArrayList<>();

    /**
     * 回滚区块和区块内的所有交易和交易产生的数据
     */
    public boolean rollbackBlock(long blockHeight) throws Exception {
        clear();
        BlockInfo blockInfo = queryBlock(blockHeight);
        if (blockInfo == null) {
            rollbackComplete();
            return true;
        }

        findAddProcessAgentOfBlock(blockInfo);

        processTxs(blockInfo.getTxs());

        roundManager.rollback(blockInfo);
        save(blockInfo);
        return false;
    }

    /**
     * 查找当前出块节点并处理相关信息
     *
     * @return
     */
    private AgentInfo findAddProcessAgentOfBlock(BlockInfo blockInfo) {
        BlockHeaderInfo headerInfo = blockInfo.getBlockHeader();
        AgentInfo agentInfo;
        if (headerInfo.isSeedPacked()) {
            //如果是种子节点打包的区块，则创建一个新的AgentInfo对象，临时使用
            agentInfo = new AgentInfo();
            agentInfo.setPackingAddress(headerInfo.getPackingAddress());
            agentInfo.setAgentId(headerInfo.getPackingAddress());
            agentInfo.setRewardAddress(agentInfo.getPackingAddress());
        } else {
            //根据区块头的打包地址，查询打包节点的节点信息，做关联存储使用
            agentInfo = queryAgentInfo(headerInfo.getPackingAddress(), 3);
        }

        agentInfo.setTotalPackingCount(agentInfo.getTotalPackingCount() - 1);
        agentInfo.setLastRewardHeight(headerInfo.getHeight() - 1);
        agentInfo.setVersion(headerInfo.getAgentVersion());

        if(!blockInfo.getTxs().isEmpty()) {
            calcCommissionReward(agentInfo, blockInfo.getTxs().get(0));
        }

        return agentInfo;
    }

    /**
     * 计算每个区块的佣金提成比例
     *
     * @param agentInfo
     * @param coinBaseTx
     */
    private void calcCommissionReward(AgentInfo agentInfo, TransactionInfo coinBaseTx) {
        List<Output> list = coinBaseTx.getTos();
        if (null == list) {
            return;
        }
        long agentReward = 0L, other = 0L;
        for (Output output : list) {
            if (output.getAddress().equals(agentInfo.getRewardAddress())) {
                agentReward += output.getValue();
            } else {
                other += output.getValue();
            }
        }

        agentInfo.setTotalReward(agentInfo.getTotalReward() - agentReward - other);
        agentInfo.setAgentReward(agentInfo.getAgentReward() - agentReward);
        agentInfo.setCommissionReward(agentInfo.getCommissionReward() - other);
    }

    private void processTxs(List<TransactionInfo> txs) throws Exception {
        for (int i = 0; i < txs.size(); i++) {
            TransactionInfo tx = txs.get(i);
            if (tx.getTos() != null) {
                for (Output output : tx.getTos()) {
                    output.setTxHash(tx.getHash());
                    outputMap.put(output.getKey(), output);
                }
            }
        }

        for (int i = 0; i < txs.size(); i++) {
            TransactionInfo tx = txs.get(i);
            processTxInputOutput(tx);

            if (tx.getType() == TransactionConstant.TX_TYPE_COINBASE) {
                processCoinBaseTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_TRANSFER) {
                processTransferTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_ALIAS) {
                processAliasTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_REGISTER_AGENT) {
                processCreateAgentTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_JOIN_CONSENSUS) {
                processDepositTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_CANCEL_DEPOSIT) {
                processCancelDepositTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_STOP_AGENT) {
                processStopAgentTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_YELLOW_PUNISH) {
                processYellowPunishTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_RED_PUNISH) {
                processRedPunishTx(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_CREATE_CONTRACT) {
                processCreateContract(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_CALL_CONTRACT) {
                processCallContract(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_DELETE_CONTRACT) {
                processDeleteContract(tx);
            } else if (tx.getType() == TransactionConstant.TX_TYPE_CONTRACT_TRANSFER) {
                processContractTransfer(tx);
            }
        }
    }

    private void processCoinBaseTx(TransactionInfo tx) {
        if (tx.getTos() == null || tx.getTos().isEmpty()) {
            return;
        }
        for (Output output : tx.getTos()) {
            AccountInfo accountInfo = queryAccountInfo(output.getAddress());
            accountInfo.setTotalIn(accountInfo.getTotalIn() - output.getValue());
            accountInfo.setTxCount(accountInfo.getTxCount() - 1);
            accountInfo.setTotalBalance(accountInfo.getTotalBalance() - output.getValue());
        }
    }

    private void processTxInputOutput(TransactionInfo tx) {
        if (tx.getFroms() != null) {
            for (Input input : tx.getFroms()) {
                // txRelationInfoSet.add(new TxRelationInfo(input.getAddress(), tx));
                //这里需要特殊处理，由于一个区块打包的多个交易中，可能存在下一个交易用到了上一个交易新生成的utxo
                //因此在这里添加进入inputList之前提前判断是否outputMap里已有对应的output，有就直接删除
                //没有才添加进入集合
                if (outputMap.containsKey(input.getKey())) {
                    outputMap.remove(input.getKey());
                } else {
                    inputList.add(input);
                }
            }
        }
    }

    private void processTransferTx(TransactionInfo tx) {
        Map<String, Long> addressMap = new HashMap<>();
        Long value;
        for (Input input : tx.getFroms()) {
            value = addressMap.get(input.getAddress()) == null ? 0L : addressMap.get(input.getAddress());
            value -= input.getValue();
            addressMap.put(input.getAddress(), value);
        }

        for (Output output : tx.getTos()) {
            value = addressMap.get(output.getAddress()) == null ? 0L : addressMap.get(output.getAddress());
            value += output.getValue();
            addressMap.put(output.getAddress(), value);
        }

        for (Map.Entry<String, Long> entry : addressMap.entrySet()) {
            AccountInfo accountInfo = queryAccountInfo(entry.getKey());
            accountInfo.setTxCount(accountInfo.getTxCount() - 1);
            value = entry.getValue();
            if (value > 0) {
                accountInfo.setTotalIn(accountInfo.getTotalIn() - value);
            } else {
                accountInfo.setTotalOut(accountInfo.getTotalOut() - Math.abs(value));
            }
            accountInfo.setTotalBalance(accountInfo.getTotalBalance() - value);
        }
    }

    private void processAliasTx(TransactionInfo tx) {
        Map<String, Long> addressMap = new HashMap<>();
        Long value;
        for (Input input : tx.getFroms()) {
            value = addressMap.get(input.getAddress()) == null ? 0L : addressMap.get(input.getAddress());
            value -= input.getValue();
            addressMap.put(input.getAddress(), value);
        }

        for (Output output : tx.getTos()) {
            value = addressMap.get(output.getAddress()) == null ? 0L : addressMap.get(output.getAddress());
            value += output.getValue();
            addressMap.put(output.getAddress(), value);
        }

        for (Map.Entry<String, Long> entry : addressMap.entrySet()) {
            AccountInfo accountInfo = queryAccountInfo(entry.getKey());
            accountInfo.setTxCount(accountInfo.getTxCount() - 1);
            value = entry.getValue();
            if (value > 0) {
                accountInfo.setTotalIn(accountInfo.getTotalIn() - value);
            } else {
                accountInfo.setTotalOut(accountInfo.getTotalOut() - Math.abs(value));
            }
            accountInfo.setTotalBalance(accountInfo.getTotalBalance() - value);
        }
        AliasInfo aliasInfo = aliasService.getAliasByAddress(tx.getFroms().get(0).getAddress());
        AccountInfo accountInfo = queryAccountInfo(aliasInfo.getAddress());
        accountInfo.setAlias(null);
        aliasInfoList.add(aliasInfo);
    }

    private void processCreateAgentTx(TransactionInfo tx) {
        AccountInfo accountInfo = queryAccountInfo(tx.getFroms().get(0).getAddress());
        accountInfo.setTxCount(accountInfo.getTxCount() - 1);
        accountInfo.setTotalOut(accountInfo.getTotalOut() - tx.getFee());
        accountInfo.setTotalBalance(accountInfo.getTotalBalance() + tx.getFee());
        //查找到代理节点，设置isNew = true，最后做存储的时候删除
        AgentInfo agentInfo = queryAgentInfo(tx.getHash(), 1);
        agentInfo.setNew(true);
    }

    private void processDepositTx(TransactionInfo tx) {
        AccountInfo accountInfo = queryAccountInfo(tx.getFroms().get(0).getAddress());
        accountInfo.setTxCount(accountInfo.getTxCount() - 1);
        accountInfo.setTotalOut(accountInfo.getTotalOut() - tx.getFee());
        accountInfo.setTotalBalance(accountInfo.getTotalBalance() + tx.getFee());
        //查找到委托记录，设置isNew = true，最后做存储的时候删除
        DepositInfo depositInfo = depositService.getDepositInfoByKey(tx.getHash() + accountInfo.getAddress());
        depositInfo.setNew(true);
        depositInfoList.add(depositInfo);
        AgentInfo agentInfo = queryAgentInfo(depositInfo.getAgentHash(), 1);
        agentInfo.setTotalDeposit(agentInfo.getTotalDeposit() - depositInfo.getAmount());
        if (agentInfo.getTotalDeposit() < 0) {
            throw new RuntimeException("data error: agent[" + agentInfo.getTxHash() + "] totalDeposit < 0");
        }
    }

    private void processCancelDepositTx(TransactionInfo tx) {
        AccountInfo accountInfo = queryAccountInfo(tx.getFroms().get(0).getAddress());
        accountInfo.setTxCount(accountInfo.getTxCount() - 1);
        accountInfo.setTotalOut(accountInfo.getTotalOut() - tx.getFee());
        accountInfo.setTotalBalance(accountInfo.getTotalBalance() + tx.getFee());

        //查询取消委托记录，再根据deleteHash反向查到委托记录
        DepositInfo cancelInfo = depositService.getDepositInfoByHash(tx.getHash());
        DepositInfo depositInfo = depositService.getDepositInfoByKey(cancelInfo.getKey());
        depositInfo.setDeleteKey(null);
        depositInfo.setDeleteHeight(0);
        cancelInfo.setNew(true);
        depositInfoList.add(depositInfo);
        depositInfoList.add(cancelInfo);

        AgentInfo agentInfo = queryAgentInfo(depositInfo.getAgentHash(), 1);
        agentInfo.setTotalDeposit(agentInfo.getTotalDeposit() + depositInfo.getAmount());
    }

    private void processStopAgentTx(TransactionInfo tx) {
        for (int i = 0; i < tx.getTos().size(); i++) {
            Output output = tx.getTos().get(i);
            AccountInfo accountInfo = queryAccountInfo(output.getAddress());
            accountInfo.setTxCount(accountInfo.getTxCount() - 1);
            if (i == 0) {
                accountInfo.setTotalBalance(accountInfo.getTotalBalance() + tx.getFee());
            }
        }

        AgentInfo agentInfo = queryAgentInfo(tx.getHash(), 4);
        agentInfo.setDeleteHash(null);
        agentInfo.setDeleteHeight(0);

        //根据节点找到委托列表
        List<DepositInfo> depositInfos = depositService.getDepositListByHash(tx.getHash());
        if (!depositInfos.isEmpty()) {
            for (DepositInfo cancelDeposit : depositInfos) {
                //需要删除的数据
                cancelDeposit.setNew(true);

                DepositInfo depositInfo = depositService.getDepositInfoByKey(cancelDeposit.getDeleteKey());
                depositInfo.setDeleteHeight(0);
                depositInfo.setDeleteKey(null);

                depositInfoList.add(cancelDeposit);
                depositInfoList.add(depositInfo);

                agentInfo.setTotalDeposit(agentInfo.getTotalDeposit() + depositInfo.getAmount());
            }
        }
    }

    private void processYellowPunishTx(TransactionInfo tx) {
        punishTxHashList.add(tx.getHash());
    }

    public void processRedPunishTx(TransactionInfo tx) {
        punishTxHashList.add(tx.getHash());

        for (int i = 0; i < tx.getTos().size(); i++) {
            Output output = tx.getTos().get(i);
            AccountInfo accountInfo = queryAccountInfo(output.getAddress());
            accountInfo.setTxCount(accountInfo.getTxCount() - 1);
        }

        PunishLog redPunish = punishService.getRedPunishLog(tx.getHash());
        //根据红牌找到被惩罚的节点
        AgentInfo agentInfo = queryAgentInfo(redPunish.getAddress(), 2);
        agentInfo.setDeleteHash(null);
        agentInfo.setDeleteHeight(0);
        agentInfoList.add(agentInfo);

        //根据节点找到委托列表
        List<DepositInfo> depositInfos = depositService.getDepositListByHash(tx.getHash());
        if (!depositInfos.isEmpty()) {
            for (DepositInfo cancelDeposit : depositInfos) {
                cancelDeposit.setNew(true);
                depositInfoList.add(cancelDeposit);

                DepositInfo depositInfo = depositService.getDepositInfoByKey(cancelDeposit.getDeleteKey());
                depositInfo.setDeleteHeight(0);
                depositInfo.setDeleteKey(null);
                depositInfoList.add(depositInfo);

                agentInfo.setTotalDeposit(agentInfo.getTotalDeposit() + depositInfo.getAmount());
            }
        }
    }

    private void processCreateContract(TransactionInfo tx) throws Exception {
        ContractInfo contractInfo = contractService.getContractInfoByHash(tx.getHash());
        contractInfo.setNew(true);

        AccountInfo accountInfo = queryAccountInfo(tx.getFroms().get(0).getAddress());
        accountInfo.setTxCount(accountInfo.getTxCount() - 1);
        accountInfo.setTotalOut(accountInfo.getTotalOut() - tx.getFee());
        accountInfo.setTotalBalance(accountInfo.getTotalBalance() + tx.getFee());

        contractTxHashList.add(tx.getHash());
        ContractResultInfo resultInfo = contractService.getContractResultInfo(tx.getHash());
        if (contractInfo.getIsNrc20() == 1 && resultInfo.getSuccess()) {
            processTokenTransfers(resultInfo.getTokenTransfers(), tx);
        }
    }

    private void processCallContract(TransactionInfo tx) throws Exception {
        processTransferTx(tx);

        contractTxHashList.add(tx.getHash());
        ContractResultInfo resultInfo = contractService.getContractResultInfo(tx.getHash());
        ContractInfo contractInfo = queryContractInfo(resultInfo.getContractAddress());
        contractInfo.setTxCount(contractInfo.getTxCount() - 1);

        if (resultInfo.getSuccess()) {
            processTokenTransfers(resultInfo.getTokenTransfers(), tx);
        }
    }

    /**
     * 处理Nrc20合约相关转账的记录
     *
     * @param tokenTransfers
     */
    private void processTokenTransfers(List<TokenTransfer> tokenTransfers, TransactionInfo tx) throws Exception {
        if (tokenTransfers == null || tokenTransfers.isEmpty()) {
            return;
        }
        tokenTransferHashList.add(tx.getHash());
        for (int i = 0; i < tokenTransfers.size(); i++) {
            TokenTransfer tokenTransfer = tokenTransfers.get(i);

            ContractInfo contractInfo = queryContractInfo(tokenTransfer.getContractAddress());
            contractInfo.setTransferCount(contractInfo.getTransferCount() - 1);

            if (tokenTransfer.getFromAddress() != null) {
                processNrc20ForAccount(contractInfo, tokenTransfer.getFromAddress(), new BigInteger(tokenTransfer.getValue()), 1);
            }

            processNrc20ForAccount(contractInfo, tokenTransfer.getToAddress(), new BigInteger(tokenTransfer.getValue()), -1);
        }
    }

    private AccountTokenInfo processNrc20ForAccount(ContractInfo contractInfo, String address, BigInteger value, int type) {
        AccountTokenInfo tokenInfo = queryAccountTokenInfo(address + contractInfo.getContractAddress());
        BigInteger balanceValue = new BigInteger(tokenInfo.getBalance());
        if (type == 1) {
            balanceValue = balanceValue.add(value);
        } else {
            balanceValue = balanceValue.subtract(value);
        }

        if (balanceValue.compareTo(BigInteger.ZERO) < 0) {
            throw new RuntimeException("data error: " + address + " token[" + contractInfo.getSymbol() + "] balance < 0");
        }
        tokenInfo.setBalance(balanceValue.toString());
        if (!accountTokenMap.containsKey(tokenInfo.getKey())) {
            accountTokenMap.put(tokenInfo.getKey(), tokenInfo);
        }

        return tokenInfo;
    }

    private void processDeleteContract(TransactionInfo tx) throws Exception {
        AccountInfo accountInfo = queryAccountInfo(tx.getFroms().get(0).getAddress());
        accountInfo.setTxCount(accountInfo.getTxCount() - 1);
        accountInfo.setTotalOut(accountInfo.getTotalOut() - tx.getFee());
        accountInfo.setTotalBalance(accountInfo.getTotalBalance() + tx.getFee());

        //首先查询合约交易执行结果
        ContractResultInfo resultInfo = contractService.getContractResultInfo(tx.getHash());

        ContractInfo contractInfo = queryContractInfo(resultInfo.getContractAddress());
        contractInfo.setTxCount(contractInfo.getTxCount() - 1);

        contractTxHashList.add(tx.getHash());

        if (resultInfo.getSuccess()) {
            contractInfo.setStatus(NulsConstant.CONTRACT_STATUS_NORMAL);
        }
    }

    private void processContractTransfer(TransactionInfo tx) throws Exception {
        processTransferTx(tx);
        ContractTransferInfo transferInfo = (ContractTransferInfo) tx.getTxData();
        ContractInfo contractInfo = queryContractInfo(transferInfo.getContractAddress());
        contractInfo.setTxCount(contractInfo.getTxCount() - 1);
    }

    public void save(BlockInfo blockInfo) throws Exception {
        //存储账户token信息
        tokenService.saveAccountTokens(accountTokenMap);
        blockHeaderService.updateStep(3);

        //存储共识节点列表
        agentService.rollbackAgentList(agentInfoList);
        blockHeaderService.updateStep(2);

        //存储智能合约记录
        contractService.rollbackContractInfos(contractInfoMap);
        blockHeaderService.updateStep(1);
        //修改账户信息表
        accountService.saveAccounts(accountInfoMap);
        blockHeaderService.updateStep(0);

        //回滾token转账信息
        tokenService.rollbackTokenTransfers(tokenTransferHashList, blockInfo.getBlockHeader().getHeight());
        //回滾合约执行结果记录
        contractService.rollbackContractResults(contractTxHashList);
        //回滾智能合約交易
        contractService.rollbackContractTxInfos(contractTxHashList);
        //回滚惩罚记录
        punishService.rollbackPunishLog(punishTxHashList, blockInfo.getBlockHeader().getHeight());
        //回滚委托记录
//        depositService

        blockHeaderService.deleteBlockHeader(blockInfo.getBlockHeader().getHeight());
    }


    private void rollbackComplete() {
        blockHeaderService.rollbackComplete();
    }

    private AccountInfo queryAccountInfo(String address) {
        AccountInfo accountInfo = accountInfoMap.get(address);
        if (accountInfo == null) {
            accountInfo = accountService.getAccountInfo(address);
        }
        if (accountInfo == null) {
            accountInfo = new AccountInfo(address);
        }
        accountInfoMap.put(address, accountInfo);
        return accountInfo;
    }

    private ContractInfo queryContractInfo(String contractAddress) throws Exception {
        ContractInfo contractInfo = contractInfoMap.get(contractAddress);
        if (contractInfo == null) {
            contractInfo = contractService.getContractInfo(contractAddress);
            contractInfoMap.put(contractInfo.getContractAddress(), contractInfo);
        }
        return contractInfo;
    }

    private AccountTokenInfo queryAccountTokenInfo(String key) {
        AccountTokenInfo accountTokenInfo = accountTokenMap.get(key);
        if (accountTokenInfo == null) {
            accountTokenInfo = tokenService.getAccountTokenInfo(key);
        }
        return accountTokenInfo;
    }

    private AgentInfo queryAgentInfo(String key, int type) {
        AgentInfo agentInfo;
        for (int i = 0; i < agentInfoList.size(); i++) {
            agentInfo = agentInfoList.get(i);

            if (type == 1 && agentInfo.getTxHash().equals(key)) {
                return agentInfo;
            } else if (type == 2 && agentInfo.getAgentAddress().equals(key)) {
                return agentInfo;
            } else if (type == 3 && agentInfo.getPackingAddress().equals(key)) {
                return agentInfo;
            } else if (type == 4 && key.equals(agentInfo.getDeleteHash())) {
                return agentInfo;
            }
        }
        if (type == 1) {
            agentInfo = agentService.getAgentByAgentHash(key);
        } else if (type == 2) {
            agentInfo = agentService.getAgentByAgentAddress(key);
        } else if (type == 3) {
            agentInfo = agentService.getAgentByPackingAddress(key);
        } else {
            agentInfo = agentService.getAgentByDeleteHash(key);
        }
        if (agentInfo != null) {
            agentInfoList.add(agentInfo);
        }
        return agentInfo;
    }

    private BlockInfo queryBlock(long height) throws Exception {
        BlockHeaderInfo headerInfo = blockHeaderService.getBlockHeaderInfoByHeight(height);
        if (headerInfo == null) {
            return null;
        }
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setBlockHeader(headerInfo);

        List<TransactionInfo> txList = new ArrayList<>();
        for (int i = 0; i < headerInfo.getTxHashList().size(); i++) {
            TransactionInfo tx = transactionService.getTx(headerInfo.getTxHashList().get(i));
            if (tx != null) {
                findTxCoinData(tx);
                txList.add(tx);
            }
        }
        blockInfo.setTxs(txList);
        return blockInfo;
    }

    private void findTxCoinData(TransactionInfo tx) throws Exception {
        TxCoinData coinData = utxoService.getTxCoinData(tx.getHash());
        if (coinData == null) {
            return;
        }
        if (StringUtils.isNotBlank(coinData.getInputsJson())) {
            tx.setFroms(JSONUtils.json2list(coinData.getInputsJson(), Input.class));
        }
        if (StringUtils.isNotBlank(coinData.getOutputsJson())) {
            tx.setTos(JSONUtils.json2list(coinData.getOutputsJson(), Output.class));
        }
    }

    private void clear() {
        inputList.clear();
        outputMap.clear();
        punishTxHashList.clear();
        accountInfoMap.clear();
        aliasInfoList.clear();
        agentInfoList.clear();
        depositInfoList.clear();
        contractInfoMap.clear();
        contractResultList.clear();
        accountTokenMap.clear();
        contractTxInfoList.clear();
        contractTxHashList.clear();
        tokenTransferHashList.clear();
    }
}