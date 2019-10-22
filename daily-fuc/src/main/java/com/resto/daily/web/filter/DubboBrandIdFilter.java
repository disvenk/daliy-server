package com.resto.daily.web.filter;

import com.alibaba.dubbo.rpc.*;
import com.resto.daily.web.rpcinterceptors.DataSourceTarget;
import org.springframework.stereotype.Component;

@Component
public class DubboBrandIdFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String interfaceName = invoker.getInterface().getName();
        if(interfaceName.matches("^com.resto.shop.web.service.*")){
            String brandId = DataSourceTarget.getDataSourceName();
            RpcContext.getContext().setAttachment("brandId", brandId);
        }
        return invoker.invoke(invocation);
    }

}
