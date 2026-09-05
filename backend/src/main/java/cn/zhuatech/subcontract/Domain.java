/* 上海如静知华信息科技有限公司 https://www.zhuatech.cn/ */
package cn.zhuatech.subcontract;
import org.springframework.stereotype.Component;
import java.util.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static cn.zhuatech.subcontract.Model.*;
import static cn.zhuatech.subcontract.Engine.*;
@Component public class Domain {
 static Map<String,Object> copy(Row r){return new LinkedHashMap<>(r.data());}
 static BigDecimal n(Row r,String k){return num(r.data(),k);}
 static BigDecimal z(Map<String,Object>d,String k){return d.containsKey(k)?num(d,k):BigDecimal.ZERO;}
 static String t(Row r,String k){return txt(r.data(),k);}
 static List<Row> linked(Engine e,User u,String module,String key,String id){return e.all(u,module).stream().filter(r->t(r,key).equals(id)).toList();}
 static void unique(Engine e,User u,String module,Map<String,Object>d,String key){require(e.all(u,module).stream().noneMatch(r->t(r,key).equalsIgnoreCase(txt(d,key))),"重复的"+key);}
 static void dates(Map<String,Object>d,String from,String to){require(!date(d,to).isBefore(date(d,from)),"结束日期不能早于开始日期");}
 static void change(Engine e,User u,Row row,String state,Map<String,Object>d,String note){e.save(u,row,state,d,"LINKED",note);}
 public void edit(Engine e,User u,Row r,Map<String,Object>d){
  if(r.module().equals("readings")){require(e.ref(u,r.data(),"job","jobs").state().equals("RUNNING")&&txt(d,"job").equals(t(r,"job")),"仅进行中的任务可以修改测量值，且不得迁移任务");require(linked(e,u,"readings","job",t(r,"job")).stream().noneMatch(x->!x.id().equals(r.id())&&t(x,"point").equals(txt(d,"point"))),"测量点编号重复");return;}
  if(r.module().equals("versions")){require(e.ref(u,r.data(),"artwork","artworks").state().equals("DRAFT"),"已送审稿件不可修改");require(txt(d,"artwork").equals(t(r,"artwork"))&&num(d,"revision").compareTo(n(r,"revision"))==0,"版本不能迁移任务或改写版本号");return;}
  for(var m:e.spec().modules())for(Row other:e.all(u,m.key()))if(!other.id().equals(r.id())&&other.data().values().stream().anyMatch(v->r.id().equals(v)))throw new Failure(409,"资料已有下游引用，请新建版本而不是改写历史");
  var fields=e.spec().module(r.module()).fields().stream().map(Field::key).toList();
  r.data().forEach((k,v)->{if(!fields.contains(k))d.put(k,v);});
  if(d.containsKey("start")&&d.containsKey("end"))dates(d,"start","end");
  if(d.containsKey("from")&&d.containsKey("to"))dates(d,"from","to");
  for(String key:List.of("serial","sku","invoice","invoiceNo","lockNo"))if(d.containsKey(key))require(e.all(u,r.module()).stream().noneMatch(x->!x.id().equals(r.id())&&t(x,key).equalsIgnoreCase(txt(d,key))),"重复唯一业务标识: "+key);
  if(d.containsKey("bonusRate"))require(num(d,"bonusRate").compareTo(num(d,"baseRate"))>=0,"达档返利率不能低于基础返利率");
  if(d.containsKey("lifeLimit"))require(num(d,"serviceEvery").compareTo(num(d,"lifeLimit"))<=0,"保养间隔不能大于寿命");
  if(d.containsKey("defects"))require(num(d,"defects").compareTo(num(d,"shots"))<=0,"不良数不能超过生产次数");
  if(d.containsKey("nps"))require(num(d,"nps").compareTo(BigDecimal.TEN)<=0&&num(d,"csat").compareTo(new BigDecimal("5"))<=0,"评价分数超出范围");
  if(d.containsKey("oxygenMin"))require(num(d,"oxygenMin").compareTo(num(d,"oxygenMax"))<0,"氧气下限须小于上限");
  if(r.module().equals("invoices"))require(e.all(u,"invoices").stream().noneMatch(x->!x.id().equals(r.id())&&t(x,"shipment").equals(txt(d,"shipment"))),"运单已关联结算账单");
  if(r.module().equals("sales")){Row program=e.ref(u,d,"program","programs");require(program.state().equals("ACTIVE")&&!date(d,"soldAt").isBefore(date(program.data(),"start"))&&!date(d,"soldAt").isAfter(date(program.data(),"end")),"协议状态或销售日期无效");}
  if(r.module().equals("jobs")){Row instrument=e.ref(u,d,"instrument","instruments"),standard=e.ref(u,d,"standard","standards");require(!instrument.state().equals("RETIRED")&&t(instrument,"unit").equals(t(standard,"unit")),"器具状态或计量单位无效");require(!date(d,"performedAt").isAfter(LocalDate.now()),"不能记录未来校准");}
  if(r.module().equals("permits")){require(ChronoUnit.DAYS.between(date(d,"start"),date(d,"end"))<=7,"许可最长七天");require(t(e.ref(u,d,"isolation","isolations"),"location").equals(txt(d,"location")),"隔离区域不匹配");}
  if(r.module().equals("responses")){require(e.all(u,"responses").stream().noneMatch(x->!x.id().equals(r.id())&&t(x,"survey").equals(txt(d,"survey"))&&t(x,"customer").equals(txt(d,"customer"))),"客户已存在该问卷反馈");require(t(e.ref(u,d,"customer","customers"),"consent").equals("YES"),"客户未允许反馈邀请");}
  if(r.module().equals("products")){String barcode=txt(d,"barcode");require(barcode.matches("\\d{13}"),"条码须为 EAN-13");int sum=0;for(int x=0;x<12;x++)sum+=(barcode.charAt(x)-'0')*(x%2==0?1:3);require((10-sum%10)%10==barcode.charAt(12)-'0',"EAN-13 校验位不正确");}

 }
 public Map<String,Object> metrics(Engine e,User u){
  var out=new LinkedHashMap<String,Object>();out.put("加工中订单",e.all(u,"orders").stream().filter(r->r.state().equals("ACTIVE")).count());out.put("待付加工费",e.all(u,"orders").stream().map(r->z(r.data(),"payable").subtract(z(r.data(),"paid"))).reduce(BigDecimal.ZERO,BigDecimal::add));out.put("供应商在外原料",e.all(u,"orders").stream().map(r->z(r.data(),"outside")).reduce(BigDecimal.ZERO,BigDecimal::add));;return out;
 }
 public void create(Engine e,User u,String module,Map<String,Object>d){switch(module){case "orders" -> {require(e.ref(u,d,"supplier","suppliers").state().equals("ACTIVE"),"供应商已暂停");for(String key:List.of("outside","accepted","rejected","payable","paid"))d.put(key,0);} default -> {} }}
 public String action(Engine e,User u,Row r,String action,Map<String,Object>i,Map<String,Object>d){
  String k=r.module()+"."+action;switch(k){
case "orders.approve" -> require(e.ref(u,d,"supplier","suppliers").state().equals("ACTIVE"),"供应商已暂停");
case "orders.issue" -> {
 Row mat=e.ref(u,d,"material","materials");BigDecimal qty=num(i,"quantity");require(qty.compareTo(n(mat,"onHand"))<=0,"原料库存不足");
 BigDecimal remaining=z(d,"quantity").subtract(z(d,"accepted")).subtract(z(d,"rejected")).multiply(z(d,"bomQty"));require(z(d,"outside").add(qty).compareTo(remaining)<=0,"发料超过剩余加工定额");
 var md=copy(mat);md.put("onHand",n(mat,"onHand").subtract(qty));change(e,u,mat,mat.state(),md,"委外发料");d.put("outside",z(d,"outside").add(qty));e.ledger(u,"movements","POSTED",Map.of("order",r.id(),"material",mat.id(),"quantity",qty,"kind","ISSUE"));
}
case "orders.returnMaterial" -> {
 BigDecimal qty=num(i,"quantity");require(qty.compareTo(z(d,"outside"))<=0,"退料超出在外材料");Row mat=e.ref(u,d,"material","materials");var md=copy(mat);md.put("onHand",n(mat,"onHand").add(qty));change(e,u,mat,mat.state(),md,"委外余料退库");d.put("outside",z(d,"outside").subtract(qty));e.ledger(u,"movements","POSTED",Map.of("order",r.id(),"material",mat.id(),"quantity",qty,"kind","RETURN"));
}
case "receipts.post" -> {
 Row order=e.ref(u,d,"order","orders");require(order.state().equals("ACTIVE"),"加工订单未生效");BigDecimal accepted=z(d,"accepted"),rejected=z(d,"rejected"),total=accepted.add(rejected);require(total.signum()>0,"收货总数须大于零");
 require(n(order,"accepted").add(n(order,"rejected")).add(total).compareTo(n(order,"quantity"))<=0,"收货超过订单总量");BigDecimal consumed=total.multiply(n(order,"bomQty"));require(consumed.compareTo(n(order,"outside"))<=0,"供应商处材料不足");
 var od=copy(order);od.put("outside",n(order,"outside").subtract(consumed));od.put("accepted",n(order,"accepted").add(accepted));od.put("rejected",n(order,"rejected").add(rejected));od.put("payable",money(n(order,"payable").add(accepted.multiply(n(order,"unitFee")))));change(e,u,order,order.state(),od,"检验收货扣耗");
 e.ledger(u,"movements","POSTED",Map.of("order",order.id(),"material",t(order,"material"),"quantity",consumed,"kind","CONSUME","receipt",r.id()));e.ledger(u,"movements","POSTED",Map.of("order",order.id(),"material",t(order,"material"),"quantity",accepted,"kind","FINISHED_RECEIPT","receipt",r.id()));d.put("consumed",consumed);d.put("processingFee",money(accepted.multiply(n(order,"unitFee"))));
}
case "payments.pay" -> {
 Row order=e.ref(u,d,"order","orders");require(order.state().equals("ACTIVE"),"订单不可付款");require(z(d,"amount").compareTo(n(order,"payable").subtract(n(order,"paid")))<=0,"付款超过未付合格加工费");
 require(e.all(u,"payments").stream().noneMatch(p->!p.id().equals(r.id())&&p.state().equals("PAID")&&t(p,"reference").equals(txt(d,"reference"))),"付款凭证重复");var od=copy(order);od.put("paid",n(order,"paid").add(z(d,"amount")));change(e,u,order,order.state(),od,"加工费支付");
}
case "orders.close" -> {require(z(d,"accepted").add(z(d,"rejected")).compareTo(z(d,"quantity"))==0,"收货数量尚未完成");require(z(d,"outside").signum()==0,"在外材料尚未退回或消耗");require(z(d,"payable").compareTo(z(d,"paid"))==0,"加工费尚未结清");}

case "materials.receive" -> {require(e.all(u,"movements").stream().noneMatch(x->t(x,"reference").equals(txt(i,"reference"))),"入库凭证重复");BigDecimal qty=num(i,"quantity");d.put("onHand",z(d,"onHand").add(qty));e.ledger(u,"movements","POSTED",Map.of("material",r.id(),"quantity",qty,"kind","RECEIVE","reference",txt(i,"reference")));} default -> {} }return null;
 }
}
