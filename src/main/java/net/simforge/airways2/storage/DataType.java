package net.simforge.airways2.storage;

public enum DataType {
    Signed32bit, // int
//    Unsigned24bit,
    Unsigned16bit, // todo ak not implemented, not tested
    Unsigned8bit,
    LatLong24bit, // accuracy 0.0001
    LatLong16bit, // accuracy 0.01
    PlainString, // todo ak no longer than 254!!!
//    PackedTo6BitsString
}
