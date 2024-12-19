package net.simforge.airways2.storage;

public enum DataType {
    Signed32bit, // int
    Unsigned24bit, // todo ak2 not implemented, not tested
    Unsigned16bit,
    Unsigned8bit,
    LatLong24bit, // accuracy 0.0001, this means 11 meters step on equator
    LatLong16bit, // accuracy 0.01, this means 1.1 kilometer step on equator
    PlainString, // todo ak2 add check that it is no longer than 254!!!
//    PackedTo6BitsString
}
