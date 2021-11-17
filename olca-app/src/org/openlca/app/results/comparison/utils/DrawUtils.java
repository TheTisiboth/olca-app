package org.openlca.app.results.comparison.utils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.openlca.app.results.comparison.display.Cell;

public class DrawUtils {
	public static int gapBetweenRect;

	/**
	 * Draw a line, with an optional color
	 * 
	 * @param gc          The GC
	 * @param start       The starting point
	 * @param end         The ending point
	 * @param beforeColor The color of the line
	 * @param afterColor  The color to get back after the draw
	 */
	public static void drawLine(GC gc, Point start, Point end, Object beforeColor, Integer afterColor) {
		if (beforeColor != null) {
			if (beforeColor instanceof Integer) {
				gc.setForeground(gc.getDevice().getSystemColor((int) beforeColor));
			} else {
				gc.setForeground(new Color(gc.getDevice(), (RGB) beforeColor));
			}
		}
		gc.drawLine(start.x, start.y, end.x, end.y);
		if (afterColor != null) {
			gc.setForeground(gc.getDevice().getSystemColor(afterColor));
		}
	}

	/**
	 * Draw a filled rectangle, with an optional color
	 * 
	 * @param gc          The GC
	 * @param start       The starting point
	 * @param width       The width
	 * @param height      The height
	 * @param beforeColor The color of the rectangle
	 * @param afterColor  The color to get back after the draw
	 */
	public static void fillRectangle(GC gc, Cell cell, Integer afterColor) {
		gc.setBackground(new Color(gc.getDevice(), cell.getRgb()));

		gc.fillRectangle(cell.x, cell.y, cell.width, cell.height);
		if (afterColor != null) {
			gc.setBackground(gc.getDevice().getSystemColor(afterColor));
		}
	}

	public static void drawRectangle(GC gc, Cell cell, int borderWidth, Integer beforeColor, Integer afterColor) {
		gc.setForeground(gc.getDevice().getSystemColor((int) beforeColor));
		gc.setLineWidth(borderWidth);
		gc.drawRectangle(cell.x, cell.y, cell.width, cell.height);
		if (afterColor != null) {
			gc.setForeground(gc.getDevice().getSystemColor(afterColor));
		}
		gc.setLineWidth(1);
	}

	public static void fillRectangle(GC gc, Rectangle cell, RGB cellColor, Integer afterColor) {
		gc.setBackground(new Color(gc.getDevice(), cellColor));

		gc.fillRectangle(cell.x, cell.y, cell.width, cell.height);
		if (afterColor != null) {
			gc.setBackground(gc.getDevice().getSystemColor(afterColor));
		}
	}

	/**
	 * Create a parallelogram from 2 points in the middle of the 2 widths, and the
	 * value of the width of the parallelogram
	 * 
	 * @param start The starting point
	 * @param end   The ending point
	 * @param width The width of the parallelogram
	 * @return [r1 : upper right edge; r2: upper left edge; r4: lower left edge; r3:
	 *         lower right edge]
	 */
	public static int[] getParallelogram(Point start, Point end, int width, Cell startingCell, Cell endingCell) {
		start.y--;
		// Calculate a vector between start and end points
		var V = new Point(end.x - start.x, end.y - start.y);
		// Then calculate a perpendicular to it
		var P = new Point(V.y, -V.x);
		// Thats length of perpendicular
		var length = Math.sqrt(P.x * P.x + P.y * P.y);

		// Normalize that perpendicular
		var N = new org.openlca.geo.geojson.Point((P.x / length), (P.y / length));
		// Compute the rectangle edges
		var r1 = new Point((int) (start.x + N.x * width / 2), (int) (start.y + N.y * width / 2));
		var r2 = new Point((int) (start.x - N.x * width / 2), (int) (start.y - N.y * width / 2));
		var r3 = new Point((int) (end.x + N.x * width / 2), (int) (end.y + N.y * width / 2));
		var r4 = new Point((int) (end.x - N.x * width / 2), (int) (end.y - N.y * width / 2));

		// Do an homothety to move the points towards the contributions bar, so they are
		// not floating over or under it
		r1.x = r1.x + ((start.y - r1.y) * V.x) / (V.y);
		r1.y = start.y;
		r2.x = r2.x + ((start.y - r2.y) * V.x) / (V.y);
		r2.y = start.y;
		r3.x = r3.x + ((end.y - r3.y) * V.x) / (V.y);
		r3.y = end.y;
		r4.x = r4.x + ((end.y - r4.y) * V.x) / (V.y);
		r4.y = end.y;

		// We need to truncate the x coordinate, because the polygon can be really
		// stretched if the 2 cells are too far away, so we bound the coordinate to the
		// cell coordinate
		int array[] = { truncatePointCoordinate(r1.x, startingCell.rectCell), r1.y,
				truncatePointCoordinate(r2.x, startingCell.rectCell), r2.y,
				truncatePointCoordinate(r4.x, endingCell.rectCell), r4.y,
				truncatePointCoordinate(r3.x, endingCell.rectCell), r3.y };
		return array;
	}

	private static int truncatePointCoordinate(int p1, Rectangle rectCell) {
		int leftEdge = rectCell.x;
		int rightEdge = rectCell.x + rectCell.width;
		int correctedPoint = p1;
		p1 = p1 < leftEdge ? leftEdge : p1;
		p1 = p1 > rightEdge ? rightEdge : p1;

		return correctedPoint;
	}

	/**
	 * Draw a bezier curve, between 2 points
	 * 
	 * @param gc    The GC component
	 * @param start The starting point
	 * @param end   The ending point
	 * @param rgb   The color of the curve
	 */
	public static void drawBezierCurve(GC gc, Point start, Point end, RGB rgb) {
		gc.setForeground(new Color(gc.getDevice(), rgb));
		Path p = new Path(gc.getDevice());
		p.moveTo(start.x, start.y);
		int offset = 100;
		Point ctrlPoint1 = new Point(start.x + offset, start.y + offset);
		Point ctrlPoint2 = new Point(end.x - offset, end.y - offset);
		p.cubicTo(ctrlPoint1.x, ctrlPoint1.y, ctrlPoint2.x, ctrlPoint2.y, end.x, end.y);
		gc.drawPath(p);
		gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
	}

}
