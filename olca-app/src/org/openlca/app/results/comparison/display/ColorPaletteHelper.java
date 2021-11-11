package org.openlca.app.results.comparison.display;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.eclipse.swt.graphics.RGB;
import org.openlca.core.model.descriptors.ProcessDescriptor;

/**
 * Color were taken from https://www.rapidtables.com/web/color/RGB_Color.html
 * 
 * @author Nutzer
 *
 */
public class ColorPaletteHelper {
	private static Map<Integer, RGB> map = initColorsMap();
	private static int NB_COLORS;
	private static long seed = (long) 42;
	private static Random rand = new Random(seed);
	private static Map<Long, RGB> processIdToRGB = new HashMap<Long, RGB>();
	private static Map<Long, RGB> locationIdToRGB = new HashMap<Long, RGB>();
	private static Map<Long, RGB> categoryIdToRGB = new HashMap<Long, RGB>();

	private static Map<Integer, RGB> initColorsMap() {
		RGB colorsPalette[] = { new RGB(255, 82, 82), new RGB(255, 64, 129), new RGB(224, 64, 251),
				new RGB(124, 77, 255), new RGB(83, 109, 254), new RGB(68, 138, 255), new RGB(64, 196, 255),
				new RGB(24, 255, 255), new RGB(100, 255, 218), new RGB(105, 240, 174), new RGB(178, 255, 89),
				new RGB(238, 255, 65), new RGB(255, 255, 0), new RGB(255, 215, 64), new RGB(255, 171, 64),
				new RGB(255, 61, 0), new RGB(121, 85, 72), new RGB(96, 125, 139) };
//		RGB colorsPalette[] = { new RGB(128, 0, 0), new RGB(139, 0, 0), new RGB(165, 42, 42), new RGB(178, 34, 34),
//				new RGB(220, 20, 60), new RGB(255, 0, 0), new RGB(255, 99, 71), new RGB(255, 127, 80),
//				new RGB(205, 92, 92), new RGB(240, 128, 128), new RGB(233, 150, 122), new RGB(250, 128, 114),
//				new RGB(255, 160, 122), new RGB(255, 69, 0), new RGB(255, 140, 0), new RGB(255, 165, 0),
//				new RGB(255, 215, 0), new RGB(184, 134, 11), new RGB(218, 165, 32), new RGB(238, 232, 170),
//				new RGB(189, 183, 107), new RGB(240, 230, 140), new RGB(128, 128, 0), new RGB(255, 255, 0),
//				new RGB(154, 205, 50), new RGB(85, 107, 47), new RGB(107, 142, 35), new RGB(124, 252, 0),
//				new RGB(127, 255, 0), new RGB(173, 255, 47), new RGB(0, 100, 0), new RGB(0, 128, 0),
//				new RGB(34, 139, 34), new RGB(0, 255, 0), new RGB(50, 205, 50), new RGB(144, 238, 144),
//				new RGB(152, 251, 152), new RGB(143, 188, 143), new RGB(0, 250, 154), new RGB(0, 255, 127),
//				new RGB(46, 139, 87), new RGB(102, 205, 170), new RGB(60, 179, 113), new RGB(32, 178, 170),
//				new RGB(47, 79, 79), new RGB(0, 128, 128), new RGB(0, 139, 139), new RGB(0, 255, 255),
//				new RGB(0, 255, 255), new RGB(0, 206, 209), new RGB(64, 224, 208), new RGB(72, 209, 204),
//				new RGB(175, 238, 238), new RGB(127, 255, 212), new RGB(176, 224, 230), new RGB(95, 158, 160),
//				new RGB(70, 130, 180), new RGB(100, 149, 237), new RGB(0, 191, 255), new RGB(30, 144, 255),
//				new RGB(173, 216, 230), new RGB(135, 206, 235), new RGB(135, 206, 250), new RGB(25, 25, 112),
//				new RGB(0, 0, 128), new RGB(0, 0, 139), new RGB(0, 0, 205), new RGB(0, 0, 255), new RGB(65, 105, 225),
//				new RGB(138, 43, 226), new RGB(75, 0, 130), new RGB(72, 61, 139), new RGB(106, 90, 205),
//				new RGB(123, 104, 238), new RGB(147, 112, 219), new RGB(139, 0, 139), new RGB(148, 0, 211),
//				new RGB(153, 50, 204), new RGB(186, 85, 211), new RGB(128, 0, 128), new RGB(216, 191, 216),
//				new RGB(221, 160, 221), new RGB(238, 130, 238), new RGB(255, 0, 255), new RGB(218, 112, 214),
//				new RGB(199, 21, 133), new RGB(219, 112, 147), new RGB(255, 20, 147), new RGB(255, 105, 180),
//				new RGB(255, 182, 193), new RGB(255, 192, 203), new RGB(112, 128, 144), new RGB(128, 128, 128),
//				new RGB(220, 220, 220) };
//		RGB colorsPalette[] = { new RGB(128, 0, 0), new RGB(220, 20, 60), new RGB(255, 0, 0), new RGB(205, 92, 92),
//				new RGB(255, 165, 0), new RGB(255, 215, 0), new RGB(184, 134, 11), new RGB(218, 165, 32),
//				new RGB(238, 232, 170), new RGB(189, 183, 107), new RGB(240, 230, 140), new RGB(128, 128, 0),
//				new RGB(255, 255, 0), new RGB(0, 100, 0), new RGB(154, 205, 50), new RGB(152, 251, 152),
//				new RGB(0, 206, 209), new RGB(47, 79, 79), new RGB(0, 128, 128), new RGB(70, 130, 180),
//				new RGB(100, 149, 237), new RGB(0, 191, 255), new RGB(0, 0, 205), new RGB(138, 43, 226),
//				new RGB(75, 0, 130), new RGB(139, 0, 139), new RGB(105, 105, 105), new RGB(211, 211, 211) };
		Map<Integer, RGB> map = new HashMap<>();
		for (int i = 0; i < colorsPalette.length; i++) {
			map.put(i, colorsPalette[i]);
		}
		NB_COLORS = map.size();
		return map;
	}

	public static RGB getColor(ProcessDescriptor process, RGB rgbToAvoid, ColorCellCriteria criteria) {
		long id = 0;
		Map<Long, RGB> idMap = null;
		switch (criteria) {
		case CATEGORY:
			id = process.category;
			idMap = categoryIdToRGB;
			break;
		case LOCATION:
			id = process.location;
			idMap = locationIdToRGB;
			break;
		case PROCESS:
			id = process.id;
			idMap = processIdToRGB;
			break;
		}
		var rgb = idMap.get(id);
		if (rgb == null) {
			RGB tmpRGB = null;
			if (rgbToAvoid == null) {
				var randomIndex = rand.nextInt(NB_COLORS);
				tmpRGB = map.get(randomIndex);
			} else {
				while (tmpRGB == null || rgbToAvoid.equals(tmpRGB)) {
					var randomIndex = rand.nextInt(NB_COLORS);
					tmpRGB = map.get(randomIndex);
				}
			}
			idMap.put(id, tmpRGB);
			return tmpRGB;
		}
		return rgb;
	}

}
