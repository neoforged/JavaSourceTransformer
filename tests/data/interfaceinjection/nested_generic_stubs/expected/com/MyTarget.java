package com;

import com.CustomInterface;
import com.InjectedInterface;

public class MyTarget<T> implements CustomInterface<com.example.TypeParameter.InnerClass>, InjectedInterface<java.util.Map.Entry<? extends com.example.WeirdSupplier<T>, com.example.Classes.Generics<T, java.lang.Integer, com.example.WeirdSupplier<?>>>> {
}
