package org.openlca.app.results.comparison.display;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.text.WordUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.db.Database;
import org.openlca.app.editors.projects.results.ProjectResultData;
import org.openlca.app.editors.projects.results.ProjectResultEditor;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.results.ResultEditor;
import org.openlca.app.results.comparison.component.ColorationCombo;
import org.openlca.app.results.comparison.component.HighlightCategoryCombo;
import org.openlca.app.results.comparison.component.ImpactCategoryTable;
import org.openlca.app.util.Controls;
import org.openlca.app.util.UI;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ImpactMethodDao;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.CategoryDescriptor;
import org.openlca.core.model.descriptors.ImpactDescriptor;
import org.openlca.core.model.descriptors.ImpactMethodDescriptor;
import org.openlca.core.model.descriptors.ProcessDescriptor;
import org.openlca.core.results.ContributionResult;

import com.google.common.collect.Lists;

public class ProductComparison {
	private Composite shell;
	private List<Contributions> contributionsList;
	private Point screenSize;
	private Config config;
	private Point scrollPoint;
	private final Point margin;
	private final int rectHeight;
	private final int gapBetweenRect;
	private int theoreticalScreenHeight;
	private ColorCellCriteria colorCellCriteria;
	private Map<Integer, Image> cacheMap;
	private Color chosenCategoryColor;
	private ContributionResult contributionResult;
	private int cutOffSize;
	private IDatabase db;
	private ImpactMethodDescriptor impactMethod;
	private TargetCalculationEnum targetCalculation;
	private boolean isCalculationStarted;
	private long chosenProcessCategory;
	private FormToolkit tk;
	private int screenWidth;
	private Canvas canvas;
	private ProjectResultData projectResultData;
	private ProcessDescriptor selectedProduct;
	private List<ImpactDescriptor> impactCategories;
	ImpactCategoryTable impactCategoryTable;
	private Map<ImpactDescriptor, List<Contributions>> impactCategoryResultsMap;

	public ProductComparison(Composite shell, FormEditor editor, TargetCalculationEnum target, FormToolkit tk) {
		this.tk = tk;
		db = Database.get();
		Cell.db = db;
		this.shell = shell;
		config = new Config(); // Comparison config
		colorCellCriteria = config.colorCellCriteria;
		targetCalculation = target;
		if (target.equals(TargetCalculationEnum.PRODUCT_SYSTEM)) {
			var e = (ResultEditor<?>) editor;
			impactMethod = e.setup.impactMethod;
			contributionResult = e.result;
		} else if (target.equals(TargetCalculationEnum.PROJECT)) {
			var e = (ProjectResultEditor) editor;
			projectResultData = e.getData();
			e.getData().result().getContributions(new ImpactDescriptor());
			impactMethod = new ImpactMethodDao(db).getDescriptor(projectResultData.project().impactMethod.id);
		}
		contributionsList = new ArrayList<>();
		cacheMap = new HashMap<>();
		chosenProcessCategory = -1;
		margin = new Point(270, 75);
		rectHeight = 30;
		gapBetweenRect = 150;
		theoreticalScreenHeight = margin.y * 2 + gapBetweenRect * 2;
		cutOffSize = 75;
		scrollPoint = new Point(0, 0);
		isCalculationStarted = false;
		impactCategoryResultsMap = new HashMap<ImpactDescriptor, List<Contributions>>();
	}

	/**
	 * Entry point of the program. Display the contributions, and draw links between
	 * each matching results
	 */
	public void display() {
		Contributions.config = config;
		Contributions.updateComparisonCriteria(colorCellCriteria);
		Cell.config = config;

		Section settingsSection = UI.section(shell, tk, "Settings");
		Composite comp = UI.sectionClient(settingsSection, tk);
		UI.gridLayout(comp, 1);

		Section canvasSection = UI.section(shell, tk, "Diagram");
		canvasSection.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		var comp2 = UI.sectionClient(canvasSection, tk);
		comp2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		var row2 = tk.createComposite(comp2);
		// UI.gridLayout(row2, 1);
		row2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		row2.setLayout(new GridLayout(1, false));

		canvas = new Canvas(row2, SWT.V_SCROLL | SWT.H_SCROLL);
		canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		var vBar = canvas.getVerticalBar();
		vBar.setMinimum(0);

		addScrollListener(canvas);
		addResizeEvent(row2, canvas);

		initImpactCategories();

		var settingsBody = tk.createComposite(comp, SWT.NULL);
		UI.gridLayout(settingsBody, 6, 50, 10);
		chooseImpactCategoriesMenu(settingsBody);
		initContributionsList();
		colorByCriteriaMenu(settingsBody);
		selectCategoryMenu(settingsBody);
		colorPickerMenu(settingsBody);
		selectCutoffSizeMenu(settingsBody);
		runCalculationButton(settingsBody, row2, canvas);
		addPaintListener(canvas);
		addToolTipListener(row2, canvas);
	}

	/**
	 * Initialize an impact Category Map, from the Impact Method
	 */
	private void initImpactCategories() {
		impactCategories = new ImpactMethodDao(db).getCategoryDescriptors(impactMethod.id);
	}

	/**
	 * Dropdown menu, allow us to chose different Impact Categories
	 * 
	 * @param row1 The menu bar
	 * @param row2 The canvas
	 */
	private void chooseImpactCategoriesMenu(Composite row1) {
//		UI.formLabel(row1, "Chosen Categories");
		impactCategoryTable = new ImpactCategoryTable(row1, impactCategories, targetCalculation);
	}

	/**
	 * Dropdown menu, allow us to chose by what criteria we want to color the cells
	 * : either by product (default), product category or location
	 * 
	 * @param row1   The menu bar
	 * @param tk2
	 * @param row2   The second part of the display
	 * @param canvas The canvas
	 */
	private void colorByCriteriaMenu(Composite row1) {
		var comp = tk.createComposite(row1);
		UI.gridLayout(comp, 1, 10, 10);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		UI.formLabel(comp, "Color cells by");
		var combo = new ColorationCombo(comp);
		combo.setNullable(false);
		combo.addSelectionChangedListener(v -> {
			if (!colorCellCriteria.name().equals(v.name)) {
				colorCellCriteria = ColorCellCriteria.getCriteria(v.name);
				Contributions.updateComparisonCriteria(colorCellCriteria);
				contributionsList.stream().forEach(c -> c.updateCellsColor());
			}
		});
	}

	/**
	 * Dropdown menu, allow us to chose a specific Process Category to color
	 * 
	 * @param row1   The menu bar
	 * @param row2   The second part of the display
	 * @param canvas The canvas
	 */
	private void selectCategoryMenu(Composite row1) {
		var categoriesRefId = contributionsList.stream().flatMap(c -> c.getList().stream()
				.filter(cell -> cell.getProcess() != null).map(cell -> cell.getProcess().category)).distinct()
				.collect(Collectors.toSet());
		var categoriesDescriptors = new CategoryDao(db).getDescriptors(categoriesRefId);
		categoriesDescriptors.sort((c1, c2) -> c1.name.compareTo(c2.name));
		var comp = tk.createComposite(row1);
		UI.gridLayout(comp, 1, 10, 10);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		UI.formLabel(comp, "Highlight Category");
		var combo = new HighlightCategoryCombo(comp, categoriesDescriptors.toArray(CategoryDescriptor[]::new));
		combo.setNullable(true);
		combo.select(null);
		combo.addSelectionChangedListener(v -> {
			if (v == null)
				chosenProcessCategory = 0;
			else
				chosenProcessCategory = v.id;
		});
	}

	/**
	 * Trigger a selection event to a combo component
	 * 
	 * @param deselect Indicate if we deselect the selected value of the combo
	 */
	private void triggerComboSelection(Combo combo, boolean deselect) {
		if (deselect) {
			combo.deselectAll();
		}
		Event event = new Event();
		event.widget = combo;
		event.display = combo.getDisplay();
		event.type = SWT.Selection;
		combo.notifyListeners(SWT.Selection, event);
	}

	/**
	 * Reset the default color of the cells
	 */
	public void resetDefaultColorCells() {
		RGB rgb = chosenCategoryColor.getRGB();
		// Reset categories colors to default (just for the one which where
		// changed)
		contributionsList.stream().forEach(c -> c.getList().stream().filter(cell -> cell.getRgb().equals(rgb))
				.forEach(cell -> cell.resetDefaultRGB()));
	}

	/**
	 * The swt widget that allows to pick a custom color
	 * 
	 * @param composite The parent component
	 */
	private void colorPickerMenu(Composite composite) {
		var comp = tk.createComposite(composite);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		UI.gridLayout(comp, 1, 10, 10);
		// Default color (pink)
		chosenCategoryColor = new Color(shell.getDisplay(), new RGB(255, 0, 255));
		UI.formLabel(comp, "Highlight color");
		Button button = tk.createButton(comp, "    ", SWT.NONE);
		button.setSize(50, 50);
		button.setBackground(chosenCategoryColor);

		Controls.onSelect(button, e -> {
			System.out.println("selected");
			// Create the color-change dialog
			ColorDialog dlg = new ColorDialog(comp.getShell());
			// Set the selected color in the dialog from
			// user's selected color
			dlg.setRGB(button.getBackground().getRGB());
			// Change the title bar text
			dlg.setText("Choose a Color");
			// Open the dialog and retrieve the selected color
			RGB rgb = dlg.open();
			if (rgb != null) {
				// Dispose the old color, create the
				// new one, and set into the label
				chosenCategoryColor.dispose();
				chosenCategoryColor = new Color(composite.getDisplay(), rgb);
				button.setBackground(chosenCategoryColor);
//				triggerComboSelection(selectCategory, false);
			}
		});
	}

	/**
	 * Spinner allowing to set the ratio of the cutoff area
	 * 
	 * @param row1   The menu bar
	 * @param row2   The second part of the display
	 * @param canvas The canvas
	 */
	private void selectCutoffSizeMenu(Composite row1) {
		var comp = UI.formComposite(row1, tk);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		UI.gridLayout(comp, 1, 10, 10);

		UI.formLabel(comp, tk, "Don't show < ");
		var comp2 = tk.createComposite(comp);
		var layout = new GridLayout(2, true);
		layout.horizontalSpacing = 15;
		layout.marginRight = 15;
		layout.marginLeft = 15;
		comp2.setLayout(layout);
		UI.gridLayout(comp2, 2, 0, 0);
		var selectCutoff = new Spinner(comp2, SWT.BORDER);
		UI.formLabel(comp2, tk, "  %");
		selectCutoff.setValues(cutOffSize, 0, 100, 0, 1, 10);
		selectCutoff.addModifyListener((e) -> {
			var newCutoffSize = selectCutoff.getSelection();
			if (newCutoffSize != cutOffSize) {
				cutOffSize = selectCutoff.getSelection();
			}
		});
	}

	/**
	 * Run the calculation, according to the selected values
	 * 
	 * @param row1   The menu bar
	 * @param row2   The second part of the display
	 * @param canvas The canvas
	 */
	private void initContributionsList() {
		var vBar = canvas.getVerticalBar();
		contributionsList = new ArrayList<>();
		if (TargetCalculationEnum.PRODUCT_SYSTEM.equals(targetCalculation)) {
			var impactCategories = impactCategoryTable.getImpactDescriptors();
			impactCategories.stream().forEach(category -> {
				var contributionList = contributionResult.getProcessContributions(category);
				var c = new Contributions(contributionList, category.name, null);
				contributionsList.add(c);
			});
		} else {
			var impactCategory = impactCategoryTable.getImpactDescriptors().get(0);
			var resultsList = impactCategoryResultsMap.get(impactCategory);
			if (resultsList == null) {
				projectResultData.project().variants.stream().forEach(v -> {
					var contributionResult = projectResultData.result().getResult(v);
					var contributionList = contributionResult.getProcessContributions(impactCategory);
					var c = new Contributions(contributionList, impactCategory.name, v.productSystem.name);
					contributionsList.add(c);
				});
				impactCategoryResultsMap.put(impactCategory, contributionsList);
			} else {
				contributionsList = resultsList;
			}
		}
		isCalculationStarted = true;
		theoreticalScreenHeight = margin.y * 2 + gapBetweenRect * (contributionsList.size());
		vBar.setMaximum(theoreticalScreenHeight);
		sortContributions();
	}

	/**
	 * Sort contributions by ascending amount, according to the comparison criteria
	 */
	private void sortContributions() {
		Contributions.updateComparisonCriteria(colorCellCriteria);
		contributionsList.stream().forEach(c -> c.sort());
	}

	/**
	 * Run the calculation, according to the selected values
	 * 
	 * @param row1   The menu bar
	 * @param row2   The second part of the display
	 * @param canvas The canvas
	 */
	private void runCalculationButton(Composite row1, Composite row2, Canvas canvas) {
		var vBar = canvas.getVerticalBar();

		var comp = tk.createComposite(row1);
		UI.gridLayout(comp, 1, 10, 10);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		tk.createLabel(comp, "");
		Button button = tk.createButton(comp, "Update Diagram", SWT.NONE);
		button.setImage(Icon.RUN.get());

		Controls.onSelect(button, e -> {
			var hash = computeConfigurationHash();
			// Cached image, in which we draw the things, and then display
			// it once it is
			// finished
			Image cache = cacheMap.get(hash);
			if (cache == null) {
				initContributionsList();
				contributionsList.stream().forEach(c -> c.getList().stream().forEach(cell -> {
					if (cell.getProcess().category == chosenProcessCategory) {
						cell.setRgb(chosenCategoryColor.getRGB());
					}
				}));
				isCalculationStarted = true;
				theoreticalScreenHeight = margin.y * 2 + gapBetweenRect * (contributionsList.size());
				vBar.setMaximum(theoreticalScreenHeight);
			}
			redraw(row2, canvas);
		});
		tk.createLabel(row1, "");
	}

	/**
	 * Redraw everything
	 * 
	 * @param composite The parent component
	 * @param canvas    The canvas
	 */
	private void redraw(Composite composite, Canvas canvas) {
		screenSize = composite.getSize();
		if (screenSize.y == 0) {
			return;
		}
		var hash = computeConfigurationHash();
		// Cached image, in which we draw the things, and then display it once
		// it is
		// finished
		Image cache = cacheMap.get(hash);
		if (cache == null) { // Otherwise, we create it, and cache it
			cache = new Image(Display.getCurrent(), screenSize.x, theoreticalScreenHeight);
			cachedPaint(composite, cache); // Costly painting, so we cache it
			var newHash = computeConfigurationHash();
			cacheMap.put(newHash, cache);
		}
		screenWidth = canvas.getClientArea().width;
		canvas.redraw();
	}

	/**
	 * Compute a hash from the different configuration element : the target, the
	 * impact categories name, etc
	 * 
	 * @return A hash
	 */
	private int computeConfigurationHash() {
		var hash = Objects.hash(targetCalculation, impactCategoryTable.getImpactDescriptors(), chosenCategoryColor,
				colorCellCriteria, cutOffSize, isCalculationStarted, chosenProcessCategory, selectedProduct);
		return hash;
	}

	/**
	 * Costly painting method. For each process contributions, it draws links
	 * between each matching results. Since it is costly, it is firstly drawed on an
	 * image. Once it is finished, we paint the image
	 * 
	 * @param composite The parent component
	 * @param cache     The cached image in which we are drawing
	 */
	private void cachedPaint(Composite composite, Image cache) {
		var gc = new GC(cache);
		gc.setAntialias(SWT.ON);
		gc.setTextAntialias(SWT.ON);
		screenSize = composite.getSize(); // Responsive behavior
		double maxRectWidth = screenSize.x * 0.85; // 85% of the screen width
		// Starting point of the first contributions rectangle
		Point rectEdge = new Point(0 + margin.x, 0 + margin.y);
		var optional = contributionsList.stream()
				.mapToDouble(c -> c.getList().stream().mapToDouble(cell -> cell.getNormalizedAmount()).sum()).max();
		double maxSumAmount = 0.0;
		if (optional.isPresent()) {
			maxSumAmount = optional.getAsDouble();
		}
		for (int contributionsIndex = 0; contributionsIndex < contributionsList.size(); contributionsIndex++) {
			handleContributions(gc, maxRectWidth, rectEdge, contributionsIndex, maxSumAmount);
			rectEdge = new Point(rectEdge.x, rectEdge.y + gapBetweenRect);
		}
		drawLinks(gc);
	}

	/**
	 * Handle the contribution for a given impact category. Draw a rectangle, write
	 * the impact category name in it, and handle the results
	 * 
	 * @param gc                 The GC component
	 * @param maxRectWidth       The maximal width for a rectangle
	 * @param rectEdge           The coordinate of the rectangle
	 * @param contributionsIndex The index of the current contributions
	 * @param maxSumAmount       The max amounts sum of the contributions
	 */
	private void handleContributions(GC gc, double maxRectWidth, Point rectEdge, int contributionsIndex,
			double maxSumAmount) {
		var p = contributionsList.get(contributionsIndex);
		int rectWidth = (int) maxRectWidth;
		Point textPos = new Point(rectEdge.x - margin.x, rectEdge.y + 6);
		if (TargetCalculationEnum.PROJECT.equals(targetCalculation)) {
			gc.drawImage(Images.get(ModelType.PRODUCT_SYSTEM), textPos.x, textPos.y + 6);
			var wrappedSystemName = WordUtils.wrap(p.getProductSystemName(), 30);
			gc.drawText(wrappedSystemName, textPos.x + 20, textPos.y);
		} else {
			gc.drawImage(Images.get(ModelType.IMPACT_CATEGORY), textPos.x, textPos.y + 6);
			var wrappedCategoryName = WordUtils.wrap(p.getImpactCategoryName(), 30);
			gc.drawText(wrappedCategoryName, textPos.x + 20, textPos.y);
		}
		handleCells(gc, rectEdge, contributionsIndex, p, rectWidth, maxSumAmount);

		// Draw an arrow above the first rectangle contributions to show the way
		// the
		// results are ordered
		if (contributionsIndex == 0) {
			drawScale(gc, maxRectWidth, rectEdge);
		}
		// Draw a rectangle for each impact categories
		gc.drawRectangle(rectEdge.x, rectEdge.y, rectWidth, rectHeight);

	}

	private void drawScale(GC gc, double maxRectWidth, Point rectEdge) {
		Point startPoint = new Point(rectEdge.x, rectEdge.y - 40);
		Point origin = startPoint;
		Point endPoint = new Point((int) (startPoint.x + maxRectWidth), startPoint.y);
		drawLine(gc, startPoint, endPoint, null, null);
		startPoint = new Point(endPoint.x - 15, endPoint.y + 15);
		drawLine(gc, startPoint, endPoint, null, null);
		startPoint = new Point(endPoint.x - 15, endPoint.y - 15);
		drawLine(gc, startPoint, endPoint, null, null);

		var offset = 5;

		startPoint = new Point(origin.x, origin.y + offset);
		endPoint = new Point(origin.x, origin.y - offset);
		drawLine(gc, startPoint, endPoint, null, null);
		var verticalOffset = 30;
		gc.drawText("0%", endPoint.x - 7, endPoint.y - verticalOffset);

		startPoint = new Point((int) (origin.x + maxRectWidth * 0.25), origin.y + offset);
		endPoint = new Point((int) (origin.x + maxRectWidth * 0.25), origin.y - offset);
		drawLine(gc, startPoint, endPoint, null, null);

		gc.drawText("25%", endPoint.x - 7, endPoint.y - verticalOffset);

		startPoint = new Point((int) (origin.x + maxRectWidth * 0.5), origin.y + offset);
		endPoint = new Point((int) (origin.x + maxRectWidth * 0.5), origin.y - offset);
		drawLine(gc, startPoint, endPoint, null, null);

		gc.drawText("50%", endPoint.x - 7, endPoint.y - verticalOffset);

		startPoint = new Point((int) (origin.x + maxRectWidth * 0.75), origin.y + offset);
		endPoint = new Point((int) (origin.x + maxRectWidth * 0.75), origin.y - offset);
		drawLine(gc, startPoint, endPoint, null, null);

		gc.drawText("75%", endPoint.x - 7, endPoint.y - verticalOffset);

		startPoint = new Point((int) (origin.x + maxRectWidth), origin.y + offset);
		endPoint = new Point((int) (origin.x + maxRectWidth), origin.y - offset);
		drawLine(gc, startPoint, endPoint, null, null);

		gc.drawText("100%", endPoint.x - 7, endPoint.y - verticalOffset);

	}

	/**
	 * Handle the cells, and display a rectangle for each of them (and merge the
	 * cutoff one in one visual cell)
	 * 
	 * @param gc                 The GC component
	 * @param rectEdge           The coordinate of the rectangle
	 * @param contributionsIndex The index of the current contributions
	 * @param contributions      The current contributions
	 * @param rectWidth          The rect width
	 * @param maxAmount          The max amounts sum of the contributions
	 * @return The new rect width
	 */
	private void handleCells(GC gc, Point rectEdge, int contributionsIndex, Contributions contributions, int rectWidth,
			double maxSumAmount) {
		var cells = contributions.getList();

		long amountCutOff = (long) (cells.size() * cutOffSize / 100.0);
		amountCutOff += cells.stream().skip(amountCutOff).filter(c -> c.getAmount() == 0).count();
		handleBigEnoughProcess(cells, rectWidth, rectEdge, gc, amountCutOff);
//		handleCutOff(cells, rectWidth, rectEdge, gc, amountCutOff);

	}

	/**
	 * Handle the non focused values : they are in the cutoff area if we don't want
	 * them to be display (by choosing an amount of process to be display), or if
	 * they are null contributions
	 * 
	 * @param cells               The list of cells
	 * @param currentRectWidth    The remaining width where we can draw
	 * @param rectEdge            The corner of the rectangle
	 * @param gc                  The GC component
	 * @param cutOffProcessAmount The amount of process in the cutoff area
	 * @param totalRectWidth
	 * @return The total width of the rect
	 */
	private void handleCutOff(List<Cell> cells, int currentRectWidth, Point rectEdge, GC gc, long cutOffProcessAmount,
			int totalRectWidth) {
		Point start = new Point(rectEdge.x + 1, rectEdge.y + 1);
		cells.stream().limit(cutOffProcessAmount).forEach(c -> c.setIsDisplayed(false));
		if (cutOffSize == 0) {
			return;
		}

		RGB rgbCutOff = new RGB(192, 192, 192); // Color for cutoff area
		int cutOffWidth = totalRectWidth - currentRectWidth;

		double normalizedCutOffAmountSum = cells.stream().limit(cutOffProcessAmount)
				.mapToDouble(cell -> Math.abs(cell.getNormalizedAmount())).sum();
		double minimumGapBetweenCells = ((double) cutOffWidth / cutOffProcessAmount);
		int chunk = -1, chunkSize = 0, newChunk = 0;
		boolean gapBigEnough = true;
		if (minimumGapBetweenCells < 1.0) {
			// If the gap is to small, we put a certain amount of results in the
			// same
			// chunk
			chunkSize = (int) Math.ceil(1 / minimumGapBetweenCells);
			gapBigEnough = false;
		}
		Point end = null;
		var newRectWidth = 0;
		for (var cellIndex = 0; cellIndex < cutOffProcessAmount; cellIndex++) {
			if (!gapBigEnough) {
				newChunk = computeChunk(chunk, chunkSize, cellIndex);
			}
			var cell = cells.get(cellIndex);
			int cellWidth = 0;
			if (cellIndex == cutOffProcessAmount - 1) {
				cellWidth = (int) (cutOffWidth - newRectWidth);
			} else if (!gapBigEnough && chunk != newChunk) {
				// We are on a new chunk, so we draw a cell with a width of 1
				// pixel
				cellWidth = 1;
			} else if (!gapBigEnough && chunk == newChunk) {
				// We stay on the same chunk, so we don't draw the cell
				cellWidth = 0;
			} else {
				var value = cell.getNormalizedAmount();
				var percentage = value / normalizedCutOffAmountSum;
				cellWidth = (int) (cutOffWidth * percentage);
			}
			newRectWidth += cellWidth;
			end = computeEndCell(start, cell, (int) cellWidth, true);
			if (gapBigEnough || !gapBigEnough && chunk != newChunk) {
				// We end the current chunk / cell
				start = end;
				chunk = newChunk;
			}
		}
		if (cutOffProcessAmount == cells.size()) {
			newRectWidth = cutOffWidth;
		}
		fillRectangle(gc, new Point(rectEdge.x + 1, rectEdge.y + 1), newRectWidth, rectHeight - 1, rgbCutOff,
				SWT.COLOR_WHITE);
//		handleNotBigEnoughProcess(cells, (int) (remainingRectWidth - newRectWidth), rectEdge, gc, cutOffProcessAmount,
//				(end != null) ? end : start, (int) newRectWidth);
	}

	/**
	 * Handle non cutoff area, but too small values, so we display them with a
	 * minimum width
	 * 
	 * @param cells                           The list of cells
	 * @param remainingRectWidth              The remaining rectangle width where we
	 *                                        can draw
	 * @param rectEdge                        The corner of the contributions
	 *                                        rectangle
	 * @param gc                              The GC component
	 * @param cutOffProcessAmount             The amount of cutoff Process
	 * @param start                           The starting point
	 * @param currentRectWidth                The current rectangle width
	 * @param currentCellIndex
	 * @param totalRectangleWidth
	 * @param notBigEnoughContributionAmount2
	 * @return The new rectangle total width
	 */
	private void handleNotBigEnoughProcess(List<Cell> cells, int currentRectWidth, Point rectEdge, GC gc,
			long cutOffProcessAmount, Point end, long notBigEnoughContributionAmount, long currentCellIndex,
			int totalRectangleWidth) {
		int minCellWidth = 3;
		if (notBigEnoughContributionAmount == 0) {
			// If there is no too small values, we skip this part
			handleCutOff(cells, currentRectWidth, rectEdge, gc, cutOffProcessAmount, totalRectangleWidth);
		}
		int chunk = -1, chunkSize = 0, newChunk = 0;
		boolean gapEnoughBig = true;
		Point start = null;
		double length = 0.1 * currentRectWidth;
		// if the length is to big, we put a certain amount of results in
		// the same
		// chunk
		double gap = length / notBigEnoughContributionAmount;
		chunkSize = (int) Math.ceil(1.0 / gap);
		gapEnoughBig = false;
		// RGB rgbCutOff = new RGB(192, 192, 192); // Color for cutoff area
		var newRectangleWidth = 0;
		for (var cellIndex = currentCellIndex; cellIndex > cutOffProcessAmount; cellIndex--) {
			if (!gapEnoughBig) {
				newChunk = computeChunk(chunk, chunkSize, (int) cellIndex);
			}
			var cell = cells.get((int) cellIndex);
			int cellWidth = 0;

			if (!gapEnoughBig && chunk != newChunk || gapEnoughBig) {
				// We are on a new chunk, so we draw a cell with a minimum width
				cellWidth = minCellWidth;
				fillRectangle(gc, start, cellWidth, rectHeight - 1, cell.getRgb(), SWT.COLOR_WHITE);
			} else if (!gapEnoughBig && chunk == newChunk) {
				// We stay on the same chunk, so we don't draw the cell
				cellWidth = 0;
			}
			newRectangleWidth += cellWidth;
			start = new Point(end.x - cellWidth, end.y);
			computeEndCell(start, cell, (int) cellWidth, true);
			if (gapEnoughBig || !gapEnoughBig && chunk != newChunk) {
				// We end the current chunk / cell
				start = end;
				chunk = newChunk;
			}
		}
		handleCutOff(cells, currentRectWidth + newRectangleWidth, rectEdge, gc, cutOffProcessAmount,
				totalRectangleWidth);
	}

	/**
	 * Handle the bigger values, by drawing cells with proportional width to the
	 * value
	 * 
	 * @param cells                 The cells list
	 * @param totalRectangleWidth   The remaining rectangle width where we can draw
	 * @param rectEdge              The corner of the rectangle
	 * @param gc                    The GC component
	 * @param currentCellIndex      The current cell index
	 * @param start                 The starting point
	 * @param currentRectangleWidth The current rectangle width
	 * @return The new rectangle width
	 */
	private void handleBigEnoughProcess(List<Cell> cells, int totalRectangleWidth, Point rectEdge, GC gc,
			long cutoffNumber) {

		if (cutOffSize == 100) {
			handleCutOff(cells, totalRectangleWidth, rectEdge, gc, cutoffNumber, totalRectangleWidth);
		}
		double cutoffRectangleSizeRatio = (cutoffNumber) / (double) cells.size() * (cutOffSize / 100.0);
		int nonCutOffWidth = (int) (totalRectangleWidth * (1 - cutoffRectangleSizeRatio));
		if (cutOffSize == 0) {
			nonCutOffWidth = totalRectangleWidth;
		}
		int minCellWidth = 3;
		double nonCutOffSum = cells.stream().skip(cutoffNumber).mapToDouble(c -> c.getNormalizedAmount()).sum();
		final double sum = nonCutOffSum;
		final var width = nonCutOffWidth;
		long notBigEnoughContributionAmount = cells.stream().skip(cutoffNumber)
				.filter(cell -> Math.abs(cell.getNormalizedAmount()) / sum * width <= minCellWidth).count();
		nonCutOffSum = cells.stream().skip(cutoffNumber + notBigEnoughContributionAmount)
				.mapToDouble(c -> Math.sqrt(c.getNormalizedAmount())).sum();

		long bigEnoughProcessNumber = cells.size() - cutoffNumber - notBigEnoughContributionAmount;

		Point end = new Point(rectEdge.x + totalRectangleWidth, rectEdge.y);
		var start = end;
		var newRectangleWidth = 0;
		for (var cellIndex = cells.size() - 1; cellIndex >= cutoffNumber+notBigEnoughContributionAmount; cellIndex--) {
			var cell = cells.get((int) cellIndex);
			int cellWidth = 0;

			var percentage = Math.sqrt(cell.getNormalizedAmount()) / nonCutOffSum;
			cellWidth = (int) (nonCutOffWidth * percentage);

			newRectangleWidth += cellWidth;
			start = new Point(end.x - cellWidth, end.y);
			fillRectangle(gc, start, cellWidth, rectHeight, cell.getRgb(), SWT.COLOR_WHITE);
			computeEndCell(start, cell, (int) cellWidth, false);
			end = start;
		}
//		handleNotBigEnoughProcess(cells, remainingRectangleWidth - newRectangleWidth, rectEdge, gc, cutoffNumber, end,
//				notBigEnoughContributionAmount, cutoffNumber + notBigEnoughContributionAmount, remainingRectangleWidth);
		handleCutOff(cells, newRectangleWidth, rectEdge, gc, cutoffNumber + notBigEnoughContributionAmount,
				totalRectangleWidth);
	}

	/**
	 * Tells in which chunk we are
	 * 
	 * @param chunk       Index of the current chunk
	 * @param chunkSize   Amount of cells in a chunk
	 * @param resultIndex The cell index
	 * @return The new chunk index
	 */
	private int computeChunk(int chunk, int chunkSize, int cellIndex) {
		// Every chunkSize, we increment the chunk
		var newChunk = (cellIndex % (int) chunkSize) == 0;
		if (newChunk == true) {
			chunk++;
		}
		return chunk;
	}

	/**
	 * Compute the end of the current cell, and set some important information about
	 * the cell
	 * 
	 * @param start     The starting point of the cell
	 * @param cell      The current cell
	 * @param cellWidth The width of the cell
	 * @return The end point of the cell
	 */
	private Point computeEndCell(Point start, Cell cell, int cellWidth, boolean isCutoff) {
		var end = new Point(start.x + cellWidth, start.y);
		var startingPoint = new Point((end.x + start.x) / 2, start.y + rectHeight);
		var endingPoint = new Point(startingPoint.x, start.y - 1);
		var cellRect = new Rectangle(start.x, start.y, cellWidth, rectHeight);
		cell.setData(startingPoint, endingPoint, cellRect, isCutoff);
		return end;
	}

	/**
	 * Draw a line, with an optional color
	 * 
	 * @param gc          The GC
	 * @param start       The starting point
	 * @param end         The ending point
	 * @param beforeColor The color of the line
	 * @param afterColor  The color to get back after the draw
	 */
	private void drawLine(GC gc, Point start, Point end, Object beforeColor, Integer afterColor) {
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
	private void fillRectangle(GC gc, Point start, int width, int heigth, Object beforeColor, Integer afterColor) {
		if (beforeColor != null) {
			if (beforeColor instanceof Integer) {
				gc.setBackground(gc.getDevice().getSystemColor((int) beforeColor));
			} else {
				gc.setBackground(new Color(gc.getDevice(), (RGB) beforeColor));
			}
		}
		gc.fillRectangle(start.x, start.y, width, heigth);
		if (afterColor != null) {
			gc.setBackground(gc.getDevice().getSystemColor(afterColor));
		}
	}

	/**
	 * Draw the links between each matching results
	 * 
	 * @param gc The GC component
	 */
	private void drawLinks(GC gc) {
		for (int contributionsIndex = 0; contributionsIndex < contributionsList.size() - 1; contributionsIndex++) {
			var cells = contributionsList.get(contributionsIndex);
			for (Cell cell : cells.getList()) {
				if (!cell.isLinkDrawable()) // Sould be not in cutoff
					continue;
				var nextCells = contributionsList.get(contributionsIndex + 1);
				// We search for a cell that has the same process
				var optional = nextCells.getList().stream().filter(next -> next.getProcess().equals(cell.getProcess()))
						.findFirst();
				if (!optional.isPresent())
					continue;
				var linkedCell = optional.get();
				if (!linkedCell.isDisplayed())
					continue;
				var startPoint = cell.getStartingLinkPoint();
				var endPoint = linkedCell.getEndingLinkPoint();
				if (cell.hasSameProduct(selectedProduct)) {
					cell.setSelected(true);
					var polygon = getPolygon(startPoint, endPoint, 7);
					gc.setBackground(new Color(gc.getDevice(), cell.getRgb()));
					gc.fillPolygon(polygon);
					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
				} else {
					cell.setSelected(false);
					if (config.useBezierCurve) {
						drawBezierCurve(gc, startPoint, endPoint, cell.getRgb());
					} else {
						drawLine(gc, startPoint, endPoint, cell.getRgb(), null);
					}
				}
			}
		}
		return;
	}

	private int[] getPolygon(Point start, Point end, int width) {
		// Calculate a vector between start and end points
		var V = new Point(end.x - start.x, end.y - start.y);
		// Then calculate a perpendicular to it
		var P = new Point(V.y, -V.x);
		// Thats length of perpendicular
		var length = Math.sqrt(P.x * P.x + P.y * P.y);
		// Normalize that perpendicular
		var N = new org.openlca.geo.geojson.Point((P.x / length), (P.y / length));
		var r1 = new Point((int) (start.x + N.x * width / 2), (int) (start.y + N.y * width / 2));
		var r2 = new Point((int) (start.x - N.x * width / 2), (int) (start.y - N.y * width / 2));
		var r3 = new Point((int) (end.x + N.x * width / 2), (int) (end.y + N.y * width / 2));
		var r4 = new Point((int) (end.x - N.x * width / 2), (int) (end.y - N.y * width / 2));
		int array[] = { r1.x, r1.y, r2.x, r2.y, r4.x, r4.y, r3.x, r3.y };
		return array;

	}

	/**
	 * Draw a bezier curve, between 2 points
	 * 
	 * @param gc    The GC component
	 * @param start The starting point
	 * @param end   The ending point
	 * @param rgb   The color of the curve
	 */
	private void drawBezierCurve(GC gc, Point start, Point end, RGB rgb) {
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

	/**
	 * Add a scroll listener to the canvas
	 * 
	 * @param canvas The canvas component
	 */
	private void addScrollListener(Canvas canvas) {
		var vBar = canvas.getVerticalBar();
		vBar.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				int vSelection = vBar.getSelection();
				int destY = -vSelection - scrollPoint.y;
				canvas.scroll(0, destY, 0, 0, canvas.getSize().x, canvas.getSize().y, false);
				scrollPoint.y = -vSelection;
			}
		});
		var hBar = canvas.getHorizontalBar();
		hBar.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				int hSelection = hBar.getSelection();
				int destX = -hSelection - scrollPoint.x;
				canvas.scroll(destX, 0, 0, 0, canvas.getSize().x, canvas.getSize().y, false);
				scrollPoint.x = -hSelection;
			}
		});
	}

	/**
	 * Add a tooltip on hover over a cell. It will display the process name, and the
	 * contribution amount
	 * 
	 * @param canvas The canvas
	 */
	private void addToolTipListener(Composite parent, Canvas canvas) {
		Listener mouseListener = new Listener() {
			public void handleEvent(Event event) {
				switch (event.type) {
				case SWT.MouseEnter:
				case SWT.MouseMove:
					for (Contributions contributions : contributionsList) {
						for (Cell cell : Lists.reverse(contributions.getList())) {
							// event contains the coordinate of the cursor,
							// but we also have to take in
							// count if we scrolled
							var cursor = new Point(event.x - scrollPoint.x, event.y - scrollPoint.y);
							// If the cursor is contained in the cell
							if (cell.contains(cursor) && cell.isDisplayed()) {
								String text = cell.getTooltip();
								if (!(text.equals(canvas.getToolTipText()))) {
									canvas.setToolTipText(text);
								}
								return;
							}
						}
					}
					canvas.setToolTipText(null);
					break;
				case SWT.MouseDown:
					for (Contributions contributions : contributionsList) {
						for (Cell cell : Lists.reverse(contributions.getList())) {
							// event contains the coordinate of the cursor,
							// but we also have to take in
							// count if we scrolled
							var cursor = new Point(event.x - scrollPoint.x, event.y - scrollPoint.y);
							// If the cursor is contained in the cell
							if (cell.contains(cursor) && cell.isDisplayed()) {
								if (selectedProduct != null) {
									var same = cell.hasSameProduct(selectedProduct);
									if (same) {
										selectedProduct = null;
									} else {
										selectedProduct = (ProcessDescriptor) cell.getResult().getContribution().item;
									}
								} else {
									selectedProduct = (ProcessDescriptor) cell.getResult().getContribution().item;
								}
								redraw(parent, canvas);
								return;
							}
						}
					}
					break;
				}
			}
		};
		canvas.addListener(SWT.MouseMove, mouseListener);
		canvas.addListener(SWT.MouseEnter, mouseListener);
		canvas.addListener(SWT.MouseDown, mouseListener);
	}

	/**
	 * Add a resize listener to the canvas
	 * 
	 * @param composite Parent composent of the canvas
	 * @param canvas    The Canvas component
	 */
	private void addResizeEvent(Composite composite, Canvas canvas) {

		canvas.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if (cacheMap.isEmpty()) {
//					triggerComboSelection(selectCategory, true);
					redraw(composite, canvas);
					// FIXME
					// This is done to fix a bug on horizontal scrollbar
					canvas.notifyListeners(SWT.Resize, e);
				}

				Rectangle client = canvas.getClientArea();
				var vBar = canvas.getVerticalBar();
				vBar.setThumb(Math.min(theoreticalScreenHeight, client.height));
				vBar.setPageIncrement(Math.min(theoreticalScreenHeight, client.height));
				vBar.setIncrement(20);
				var hBar = canvas.getHorizontalBar();
				hBar.setMinimum(0);
				hBar.setThumb(Math.min(screenWidth, client.width));
				hBar.setPageIncrement(Math.min(screenWidth, client.width));
				hBar.setIncrement(20);
				hBar.setMaximum(screenWidth);

				int vPage = canvas.getSize().y - client.height;
				int hPage = canvas.getSize().x - client.width;
				int vSelection = vBar.getSelection();
				int hSelection = hBar.getSelection();
				if (vSelection >= vPage) {
					if (vPage <= 0)
						vSelection = 0;
					scrollPoint.y = -vSelection;
				}
				if (hSelection >= hPage) {
					if (hPage <= 0)
						hSelection = 0;
					scrollPoint.x = -hSelection;
				}
			}
		});
	}

	/**
	 * Add a paint listener to the canvas. This is called whenever the canvas needs
	 * to be redrawed, then it draws the cached image
	 * 
	 * @param canvas A Canvas component
	 */
	private void addPaintListener(Canvas canvas) {
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				var hash = computeConfigurationHash();
				var cache = cacheMap.get(hash);
				if (cache != null)
					e.gc.drawImage(cache, scrollPoint.x, scrollPoint.y);
				else
					System.out.println("Draw image failed");
			}
		});
	}
}