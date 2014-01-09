package rvolib;

import tt.euclid2i.Point;

public class Vector2 {
    float x_;
    float y_;

    public float x() {
        return x_;
    }

    public float y() {
        return y_;
    }

    public String toString() {
        return "(" + x_ + "," + y_ + ")";
    }

    public Vector2(float x, float y) {
        x_ = x;
        y_ = y;
    }

    public Vector2() {
        // TODO Auto-generated constructor stub
    }

    public Vector2(Point point) {
        x_ = point.x;
        y_ = point.y;
    }

    public Vector2(Vector2 v) {
        x_ = v.x_;
        y_ = v.y_;
    }

    public static Vector2 plus(Vector2 lhs, Vector2 rhs) {
        return new Vector2(lhs.x_ + rhs.x_, lhs.y_ + rhs.y_);
    }

    public static Vector2 minus(Vector2 lhs, Vector2 rhs) {
        return new Vector2(lhs.x_ - rhs.x_, lhs.y_ - rhs.y_);
    }

    public static float dot(Vector2 lhs, Vector2 rhs) {
        return lhs.x_ * rhs.x_ + lhs.y_ * rhs.y_;
    }

    public static Vector2 scale(float k, Vector2 u) {
        return new Vector2(k * u.x_, k * u.y_);
    }

    public static Vector2 divide(Vector2 u, float k) {
        return new Vector2(u.x_ / k, u.y_ / k);
    }

    public static Vector2 invert(Vector2 v) {
        return new Vector2(-v.x_, -v.y_);
    }

    public static void copy(Vector2 target, Vector2 source) {
        target.x_ = source.x_;
        target.y_ = source.y_;
    }

    public Point convertToPoint2i() {
        return new Point(Math.round(x_), Math.round(y_));
    }

    public double getLength() {
        return Math.sqrt(Math.pow(x_, 2) + Math.pow(y_, 2));
    }
}