package com.resto.daily.web.enity;

import java.io.Serializable;

/**
 * BaseResult : 响应的结果对象
 *

 */
public class BaseResult implements Serializable {

    private static final long serialVersionUID = 3288474646731788751L;

    //自定义业务状态码
    public static final  int SUCCESSCODE = 200;//正常状态返回

    public static final  int ERRORCODE = 500;//服务器异常

    public static final  int NOTFOUNDCODE = 404;//找不到url

    //自定义信息
    private static  final String SUCCESSMSG = "ok";

    private static  final String ERRORMSG = "服务器异常,请稍后重试";


    /**
     * 相应消息
     */
    private String msg;

    /**
     * 业务状态码
     */
    private int code;

    //相应中的数据
    private Object data;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public BaseResult(){

    }

    public BaseResult(Integer code, String msg, Object data){
        this.code=code;
        this.msg=msg;
        this.data=data;
    }

    public BaseResult(Object data){
        this.code=SUCCESSCODE;
        this.msg=SUCCESSMSG;
        this.data=data;
    }

    /**
     * 正常返回
     * 且有数据
     * @param data
     * @return
     */
    public  static  BaseResult ok(Object data){
        return new BaseResult(data);
    }

    /**
     * 正常返回
     * @return
     */
    public  static  BaseResult ok(){
        return new BaseResult(null);
    }

    /**
     * 异常返回
     * @return
     */
    public static  BaseResult build(Integer code,String msg){
        return new BaseResult(code,msg,null);
    }

    /**
     * 异常返回
     * 且有数据
     */

    public  static  BaseResult build(Integer code,String msg,Object data){
        return new BaseResult(code,msg,data);
    }
}
