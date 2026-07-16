package dev.tobyscamera.fabric.viewfinder;
public enum CaptureMode { PHOTO, VIDEO; public CaptureMode next(){return this==PHOTO?VIDEO:PHOTO;} }
