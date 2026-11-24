package me.collective.client.render;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

public interface TextColorSupplierAccess {
   @Nullable
   Supplier getSupplier();

   void setSupplier(@Nullable Supplier supplier);
}
