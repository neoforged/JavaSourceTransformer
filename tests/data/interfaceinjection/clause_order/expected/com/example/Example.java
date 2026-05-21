package com.example;

import com.example.InjectedInterface;

class Example implements InjectedInterface permits Permitted {

}

final class Permitted extends Example {}
