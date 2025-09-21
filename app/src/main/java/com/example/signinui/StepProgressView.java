package com.example.signinui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class StepProgressView extends View {

    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint textPaint;
    private RectF oval;

    private int progress = 0;       // 0–100
    private int progressColor;
    private int backgroundColor;
    private int textColor;
    private float strokeWidth;

    public StepProgressView(Context context) {
        super(context);
        init(null);
    }

    public StepProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public StepProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        // Default colors
        progressColor = 0xFF4CAF50;    // Green
        backgroundColor = 0xFFE0E0E0;  // Gray
        textColor = 0xFF2C2C2C;        // Dark text
        strokeWidth = 20f;

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StepProgressView);
            progressColor = a.getColor(R.styleable.StepProgressView_progressColor, progressColor);
            backgroundColor = a.getColor(R.styleable.StepProgressView_backgroundColor, backgroundColor);
            textColor = a.getColor(R.styleable.StepProgressView_textColor, textColor);
            strokeWidth = a.getDimension(R.styleable.StepProgressView_strokeWidth, strokeWidth);
            a.recycle();
        }

        // Paint for background circle
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);

        // Paint for progress arc
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // Paint for text
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(48f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        oval = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        float padding = strokeWidth / 2 + 10;

        oval.set(padding, padding, size - padding, size - padding);

        // Draw background circle
        canvas.drawArc(oval, 0, 360, false, backgroundPaint);

        // Draw progress arc
        float sweepAngle = (progress / 100f) * 360f;
        canvas.drawArc(oval, -90, sweepAngle, false, progressPaint);

        // Draw text (percentage)
        String text = progress + "%";
        float x = width / 2f;
        float y = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2;
        canvas.drawText(text, x, y, textPaint);
    }

    // --- Public methods ---
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, 100));
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(color);
        invalidate();
    }

    public void setBackgroundColorCustom(int color) {
        backgroundColor = color;
        backgroundPaint.setColor(color);
        invalidate();
    }

    public void setTextColorCustom(int color) {
        textColor = color;
        textPaint.setColor(color);
        invalidate();
    }
}
