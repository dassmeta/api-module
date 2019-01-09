/*
 * MIT License
 * Copyright (c) 2017-2019 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.api.controller.account;

import io.nuls.api.bean.annotation.Autowired;
import io.nuls.api.bean.annotation.Controller;
import io.nuls.api.bean.annotation.RpcMethod;
import io.nuls.api.controller.model.RpcErrorCode;
import io.nuls.api.controller.model.RpcResult;
import io.nuls.api.controller.model.RpcResultError;
import io.nuls.api.controller.utils.VerifyUtils;
import io.nuls.api.core.model.AccountInfo;
import io.nuls.api.core.model.TxRelationInfo;
import io.nuls.api.service.AccountService;
import io.nuls.api.utils.JsonRpcException;
import io.nuls.sdk.core.utils.StringUtils;

import java.util.List;

/**
 * @author Niels
 */
@Controller
public class AccountController {

    @Autowired
    private AccountService accountService;

    @RpcMethod("getAccount")
    public RpcResult getAccount() {


        return null;
    }

    @RpcMethod("getAccountList")
    public RpcResult getAccountList(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int pageIndex = (int) params.get(0);
        int pageSize = (int) params.get(1);
        if (pageIndex <= 0) {
            pageIndex = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 10;
        }

        List<AccountInfo> accountInfoList = accountService.pageQuery(pageIndex, pageSize);
        RpcResult result = new RpcResult();
        result.setResult(accountInfoList);
        return result;
    }


    @RpcMethod("getAccountTxs")
    public RpcResult getAccountTxs(List<Object> params) {
        VerifyUtils.verifyParams(params, 5);
        String address = (String) params.get(0);
        if (StringUtils.isBlank(address)) {
            throw new JsonRpcException(new RpcResultError(RpcErrorCode.PARAMS_ERROR, "[address] is required"));
        }

        int pageIndex = (int) params.get(1);
        int pageSize = (int) params.get(2);
        if (pageIndex <= 0) {
            pageIndex = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 10;
        }

        int type = (int) params.get(3);
        boolean isMark = (boolean) params.get(4);

        List<TxRelationInfo> relationInfos = accountService.getAccountTxs(address, pageIndex, pageSize, type, isMark);
        RpcResult result = new RpcResult();
        result.setResult(relationInfos);
        return result;
    }
}
