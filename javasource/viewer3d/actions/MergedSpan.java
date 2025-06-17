package viewer3d.actions;

import java.util.ArrayList;

import com.mendix.viewer3d.jtreader.Span;

public class MergedSpan {
    private ArrayList<Span> _spans;

    public ArrayList<Span> spans() {
        return this._spans;
    }

    private ArrayList<Integer> _indices;

    public ArrayList<Integer> indices() {
        return this._indices;
    }

    public MergedSpan(ArrayList<Span> spans, ArrayList<Integer> indices) {
        this._spans = spans;
        this._indices = indices;
    }
}
