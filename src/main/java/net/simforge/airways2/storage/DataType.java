package net.simforge.airways2.storage;

public enum DataType {
    Signed32bit, // int
//    Unsigned24bit,
    Unsigned16bit,
    Unsigned8bit,
    LatLong24bit,
//    LatLong16bit,
    PlainString, // todo ak no longer than 254!!!
//    PackedTo6BitsString
}
