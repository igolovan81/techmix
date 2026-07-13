<#import "layout.ftlh" as layout>
<@layout.page title="Products">
<table>
<tr><th>Name</th><th>Price</th><th>Stock</th></tr>
<#list products as product>
<tr><td>${product.name()}</td><td>$${product.price()?string("0.00")}</td><td>${product.stock()}</td></tr>
</#list>
</table>
</@layout.page>
