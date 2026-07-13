<#macro productRow item>
<li>${item.productName()} x ${item.quantity()} = $${item.lineTotal()?string("0.00")}</li>
</#macro>
