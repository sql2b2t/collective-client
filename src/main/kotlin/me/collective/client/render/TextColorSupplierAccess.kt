package me.collective.client.render

import java.util.function.Supplier

interface TextColorSupplierAccess {
    fun getSupplier(): Supplier<*>?

    fun setSupplier(supplier: Supplier<*>?)
}
