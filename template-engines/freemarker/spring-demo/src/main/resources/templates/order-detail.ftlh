<#import "layout.ftlh" as layout>
<#import "macros/product-row.ftlh" as rows>
<@layout.page title="Order ${order.id()}">
<h2>Order ${order.id()} for ${order.customer()}</h2>
<#if order.items()?has_content>
<ul>
<#list order.items() as item>
<@rows.productRow item=item/>
</#list>
</ul>
<#else>
<p>No items.</p>
</#if>
<p>Status: ${order.status()!"pending"}</p>
<p>Total: $${order.total()?string("0.00")}</p>
</@layout.page>
