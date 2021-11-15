package org.openlca.app.results.comparison.display;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.App;
import org.openlca.app.M;
import org.openlca.app.db.Database;
import org.openlca.app.editors.projects.ProjectResultData;
import org.openlca.app.editors.projects.results.ProjectResultEditor;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.rcp.images.Images;
import org.openlca.app.results.ResultEditor;
import org.openlca.app.results.comparison.InfoSection;
import org.openlca.app.results.comparison.component.ColorationCombo;
import org.openlca.app.results.comparison.component.HighlightCategoryCombo;
import org.openlca.app.results.comparison.component.ImpactCategoryTable;
import org.openlca.app.util.Controls;
import org.openlca.app.util.ErrorReporter;
import org.openlca.app.util.MsgBox;
import org.openlca.app.util.UI;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ImpactMethodDao;
import org.openlca.core.model.ImpactMethod;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.core.model.descriptors.CategoryDescriptor;
import org.openlca.core.model.descriptors.ImpactDescriptor;
import org.openlca.core.results.Contribution;
import org.openlca.core.results.ContributionResult;

import com.google.common.collect.Lists;

public class ProductComparison {
	private Composite shell;
	private List<Contributions> contributionsList;
	private Rectangle screenSize;
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
	private double cutOffSize;
	private IDatabase db;
	private ImpactMethod impactMethod;
	private TargetCalculationEnum targetCalculation;
	private boolean isCalculationStarted;
	private long chosenProcessCategory;
	private FormToolkit tk;
	private int canvasWidth;
	private Canvas canvas;
	private ProjectResultData projectResultData;
	private Cell selectedCell;
	private List<ImpactDescriptor> impactCategories;
	ImpactCategoryTable impactCategoryTable;
	private Map<ImpactDescriptor, List<Contributions>> impactCategoryResultsMap;
	private HighlightCategoryCombo highlighCategoryCombo;
	private Map<Integer, List<Contributions>> contributionsMap;
	private Composite row2;
	private Image cachedImage;
	private Composite captionBody;
	private Section captionSection;

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
			impactMethod = e.setup.impactMethod();
			contributionResult = e.result;
		} else if (target.equals(TargetCalculationEnum.PROJECT)) {
			var e = (ProjectResultEditor) editor;
			projectResultData = e.getData();
//			e.getData().result().getContributions(new ImpactDescriptor());
			impactMethod = projectResultData.project().impactMethod;
		}
		contributionsList = new ArrayList<>();
		cacheMap = new HashMap<>();
		chosenProcessCategory = -1;
		margin = new Point(270, 75);
		rectHeight = 30;
		gapBetweenRect = 150;
		theoreticalScreenHeight = margin.y * 2 + gapBetweenRect;
		cutOffSize = 1.0;
		scrollPoint = new Point(0, 0);
		isCalculationStarted = false;
		impactCategoryResultsMap = new HashMap<ImpactDescriptor, List<Contributions>>();
		contributionsMap = new HashMap<Integer, List<Contributions>>();
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

		captionSection = UI.section(shell, tk, "Selected process contribution");
		captionBody = UI.sectionClient(captionSection, tk);
		updateCaption();

		Section canvasSection = UI.section(shell, tk, "Diagram");
		canvasSection.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		var comp2 = UI.sectionClient(canvasSection, tk);
		comp2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		row2 = tk.createComposite(comp2);
		row2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		row2.setLayout(new GridLayout(1, false));

		canvas = new Canvas(row2, SWT.V_SCROLL | SWT.H_SCROLL);
		canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		canvas.addListener(SWT.Resize, new Listener() {

			@Override
			public void handleEvent(Event event) {
				// TODO Auto-generated method stub
				System.out.println("canvas resize");
				System.out.println(canvas.getSize());
			}
		});
		// Set white background
		canvas.setBackground(new Color(Display.getCurrent(), new RGB(255, 255, 255)));

		var vBar = canvas.getVerticalBar();
		vBar.setMinimum(0);

		addScrollListener();
		addResizeEvent(row2, canvas);

		initImpactCategories();

		var settingsBody = tk.createComposite(comp, SWT.NULL);
		var layout = new GridLayout(5, false);
		layout.horizontalSpacing = 50;
		layout.marginWidth = 10;
		layout.marginHeight = 10;
		settingsBody.setLayout(layout);
		chooseImpactCategoriesMenu(settingsBody);
		initContributionsList();
		colorByCriteriaMenu(settingsBody);
		selectCategoryMenu(settingsBody);
		colorPickerMenu(settingsBody);
		selectCutoffSizeMenu(settingsBody);
		runCalculationButton(settingsBody, row2);
		addPaintListener();
		addToolTipListener(row2);
//		settingsBody.requestLayout();
	}

	/**
	 * Initialize an impact Category Map, from the Impact Method
	 */
	private void initImpactCategories() {
		if (impactMethod == null)
			return;
		impactCategories = new ImpactMethodDao(db).getCategoryDescriptors(impactMethod.id);
	}

	/**
	 * Dropdown menu, allow us to chose different Impact Categories
	 * 
	 * @param row1 The menu bar
	 * @param row2 The canvas
	 */
	private void chooseImpactCategoriesMenu(Composite row1) {
		if (impactCategories == null)
			return;
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
		UI.formLabel(comp, "Color by");
		var combo = new ColorationCombo(comp);
		combo.setNullable(false);
		combo.addSelectionChangedListener(v -> {
			if (!colorCellCriteria.name().equals(v.name)) {
				colorCellCriteria = ColorCellCriteria.getCriteria(v.name);
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
		var comp = tk.createComposite(row1);
		UI.gridLayout(comp, 1, 10, 10);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		UI.formLabel(comp, "Highlight process category");
		CategoryDescriptor[] tab = {};
		highlighCategoryCombo = new HighlightCategoryCombo(comp, db, tab);
		highlighCategoryCombo.setNullable(true);
		highlighCategoryCombo.select(null);
		highlighCategoryCombo.selectionChanged(null);
		highlighCategoryCombo.addSelectionChangedListener(v -> {
			if (v == null) {
				chosenProcessCategory = 0;
				var categoriesRefId = contributionsList.stream()
						.flatMap(c -> c.getList().stream()
								.filter(cell -> cell.isLinkDrawable() && cell.getProcess() != null)
								.map(cell -> cell.getProcess().category))
						.distinct().collect(Collectors.toSet());
				var categoriesDescriptors = new CategoryDao(db).getDescriptors(categoriesRefId);
				categoriesDescriptors.sort((c1, c2) -> c1.name.compareTo(c2.name));
				highlighCategoryCombo.setInput(categoriesDescriptors.toArray(CategoryDescriptor[]::new));

			} else {
				chosenProcessCategory = v.id;
			}
		});
	}

	/**
	 * Reset the default color of the cells
	 */
	public void resetDefaultColorCells() {
		RGB rgb = chosenCategoryColor.getRGB();
		// Reset categories colors to default (just for the one which where changed)
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
		selectCutoff.setValues((int) cutOffSize * 10, 1, 1000, 1, 1, 1);
		selectCutoff.addModifyListener((e) -> {
			var newCutoffSize = selectCutoff.getSelection();
			var digits = selectCutoff.getDigits();
			var value = (newCutoffSize / Math.pow(10, digits));
			if (value != cutOffSize) {
				cutOffSize = value;
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
			var impactIndex = contributionResult.impactIndex();
			if (impactMethod == null)
				return;
			var impactCategories = impactCategoryTable.getImpactDescriptors();
			var totalImpactResults = contributionResult.totalImpactResults;
//			var contribList = getContributionsList(impactCategories);
//			contribList.forEach(withCounter((i,contribs) -> {
//				var category = impactCategories.get(i);
//				var idx = 0;
//				while (idx < impactIndex.size()) {
//					var cat = impactIndex.at(idx);
//					if (cat.equals(category))
//						break;
//					idx++;
//				}
//				var c = new Contributions(contribs, null, category, totalImpactResults[idx]);
//				contributionsList.add(c);
//			}));
			impactCategories.stream().forEach(category -> {
				var contributionList = contributionResult.getProcessContributions(category);
				var idx = 0;
				while (idx < impactIndex.size()) {
					var cat = impactIndex.at(idx);
					if (cat.equals(category))
						break;
					idx++;
				}
				var c = new Contributions(contributionList, null, category, totalImpactResults[idx]);
				contributionsList.add(c);
			});
		} else {
			selectedCell = null;
			updateCaption();
			var impactCategory = impactCategoryTable.getImpactDescriptors().get(0);
			var resultsList = impactCategoryResultsMap.get(impactCategory);
			if (resultsList == null) {
				projectResultData.project().variants.stream().forEach(v -> {
					var contributionResult = projectResultData.result().getResult(v);
					var impactIndex = contributionResult.impactIndex();
					var totalImpactResults = contributionResult.totalImpactResults;
					var contributionList = contributionResult.getProcessContributions(impactCategory);
					var idx = 0;
					while (idx < impactIndex.size()) {
						var cat = impactIndex.at(idx);
						if (cat.equals(impactCategory))
							break;
						idx++;
					}
					var c = new Contributions(contributionList, v.productSystem.name, impactCategory,
							totalImpactResults[idx]);
					contributionsList.add(c);
				});
				impactCategoryResultsMap.put(impactCategory, contributionsList);
			} else {
				contributionsList = resultsList;
			}
		}
		isCalculationStarted = true;
		theoreticalScreenHeight = margin.y * 2 + gapBetweenRect * (contributionsList.size() - 1);
		vBar.setMaximum(theoreticalScreenHeight);
		sortContributions();
	}

	private List<List<Contribution<CategorizedDescriptor>>> getContributionsList(
			List<ImpactDescriptor> impactCategories) {
		List<List<Contribution<CategorizedDescriptor>>> contributionsList = new ArrayList<List<Contribution<CategorizedDescriptor>>>();
		Runnable calculation = () -> {
			try {
				impactCategories.forEach(impactCategory -> {
					var contributions = contributionResult.getProcessContributions(impactCategory);
					contributionsList.add(contributions);
				});

			} catch (OutOfMemoryError e) {
				MsgBox.error(M.OutOfMemory, M.CouldNotAllocateMemoryError);
			} catch (MathIllegalArgumentException e) {
				MsgBox.error("Matrix error", e.getMessage());
			} catch (Exception e) {
				ErrorReporter.on("Calculation failed", e);
			}
		};
		App.runWithProgress(M.Calculate, calculation, () -> {
			System.out.println(0);
		});

		return contributionsList;
	}

	public static <T> Consumer<T> withCounter(BiConsumer<Integer, T> consumer) {
		AtomicInteger counter = new AtomicInteger(0);
		return item -> consumer.accept(counter.getAndIncrement(), item);
	}

	/**
	 * Sort contributions by ascending amount, according to the comparison criteria
	 */
	private void sortContributions() {
		Contributions.updateComparisonCriteria(colorCellCriteria);
		contributionsList.stream().forEach(c -> c.sort());
		System.out.println("sorted");
	}

	/**
	 * Run the calculation, according to the selected values
	 * 
	 * @param row1   The menu bar
	 * @param row2   The second part of the display
	 * @param canvas The canvas
	 */
	private void runCalculationButton(Composite row1, Composite row2) {
		var vBar = canvas.getVerticalBar();

		var comp = tk.createComposite(row1);
		UI.gridLayout(comp, 1, 10, 10);
		comp.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		tk.createLabel(comp, "");
		Button button = tk.createButton(comp, "Update diagram", SWT.NONE);
		button.setImage(Icon.RUN.get());

		Controls.onSelect(button, e -> {
			var hash = computeConfigurationHash();
			// Cached image, in which we draw the things, and then display it once it is
			// finished
			Image cache = cacheMap.get(hash);
			Contributions.updateComparisonCriteria(colorCellCriteria);
			contributionsList.stream().forEach(c -> c.updateCellsColor());
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
				contributionsMap.put(hash, contributionsList);
			} else {
				var contributionsList_tmp = contributionsMap.get(hash);
				if (contributionsList_tmp != null) {
					contributionsList = contributionsList_tmp;
				}
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
		canvasWidth = composite.getClientArea().width;
//		if (composite.getClientArea().height == 0) {
//			return;
//		}
		var hash = computeConfigurationHash();
		// Cached image, in which we draw the things, and then display it once it is
		// finished
		cachedImage = cacheMap.get(hash);
		if (cachedImage == null) { // Otherwise, we create it, and cache it
			cachedImage = new Image(Display.getCurrent(), canvasWidth, theoreticalScreenHeight);
			cachedPaint(composite, cachedImage); // Costly painting, so we cache it
			var newHash = computeConfigurationHash();
			cacheMap.put(newHash, cachedImage);
			contributionsMap.put(newHash, contributionsList);
		} else {
			var contributionsList_tmp = contributionsMap.get(hash);
			if (contributionsList_tmp != null) {
				contributionsList = contributionsList_tmp;
			}
		}

		handleScroll();
		canvas.redraw();
		highlighCategoryCombo.updateCategories(contributionsList);
	}

	/**
	 * Compute a hash from the different configuration element : the target, the
	 * impact categories name, etc
	 * 
	 * @return A hash
	 */
	private int computeConfigurationHash() {
		if (impactCategoryTable != null)
			return Objects.hash(targetCalculation, impactCategoryTable.getImpactDescriptors(), chosenCategoryColor,
					Contributions.criteria, cutOffSize, isCalculationStarted, chosenProcessCategory, selectedCell,
					screenSize);
		return -1;
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
		screenSize = composite.getClientArea(); // Responsive behavior
		double maxRectWidth = screenSize.width * 0.85; // 85% of the screen width
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
		p.setBounds(rectEdge.x, rectEdge.y, rectWidth, rectHeight);
		Point textPos = new Point(rectEdge.x - margin.x, rectEdge.y + 6);
		if (TargetCalculationEnum.PROJECT.equals(targetCalculation)) {
			gc.drawImage(Images.get(ModelType.PRODUCT_SYSTEM), textPos.x, textPos.y + 1);
			var wrappedSystemName = WordUtils.wrap(p.getProductSystemName(), 27);
			wrappedSystemName += WordUtils
					.wrap("\nTotal impact: " + p.totalImpactResults + " " + p.getImpactCategory().referenceUnit, 27);
			gc.drawText(wrappedSystemName, textPos.x + 20, textPos.y);
		} else {
			gc.drawImage(Images.get(ModelType.IMPACT_CATEGORY), textPos.x, textPos.y + 1);
			var wrappedCategoryName = WordUtils.wrap(p.getImpactCategoryName(), 27);
			wrappedCategoryName += WordUtils
					.wrap("\nTotal impact: " + p.totalImpactResults + " " + p.getImpactCategory().referenceUnit, 27);
			gc.drawText(wrappedCategoryName, textPos.x + 20, textPos.y);
		}

		// Draw a rectangle for each impact categories
		gc.drawRectangle(rectEdge.x, rectEdge.y, rectWidth, rectHeight);
		var innerRect = new Point(rectEdge.x + 1, rectEdge.y + 1);
		handleCells(gc, innerRect, contributionsIndex, p, rectWidth - 2, maxSumAmount);

		// Draw an arrow above the first rectangle contributions to show the way the
		// results are ordered
//		if (contributionsIndex == 0) {
//			drawScale(gc, maxRectWidth, rectEdge);
//		}

	}

	private void drawScale(GC gc, double maxRectWidth, Point rectEdge) {
		Point startPoint = new Point(rectEdge.x, rectEdge.y - 40);
		Point origin = startPoint;
		Point endPoint = new Point((int) (startPoint.x + maxRectWidth), startPoint.y);
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
//		long nonCutOffNumber = (long) Math
//				.ceil(cells.stream().filter(c -> c.getAmount() != 0).count() * (1 - cutOffSize / 100.0));
		double cutoffValue = contributions.totalImpactResults * ((double) cutOffSize / 100);
		long nonCutoffNumber = cells.stream().filter(c -> Math.abs(c.getAmount()) >= cutoffValue).count();
		handleNonCutOff(cells, rectWidth, rectEdge, gc, nonCutoffNumber);

	}

	/**
	 * Handle the bigger values, by drawing cells with proportional width to the
	 * contribution
	 * 
	 * @param cells               The cells list
	 * @param totalRectangleWidth The length of the whole rectangle containing the
	 *                            cells
	 * @param rectEdge            The left upper edge of the rectangle
	 * @param gc                  The GC component
	 * @param nonCutoffNumber     The number of non cutoff processes
	 */
	private void handleNonCutOff(List<Cell> cells, int totalRectangleWidth, Point rectEdge, GC gc,
			long nonCutoffNumber) {
		if (cutOffSize == 100 || nonCutoffNumber == 0) {
//			handleCutOff(cells, totalRectangleWidth, rectEdge, gc, cells.size(), totalRectangleWidth);
			return;
		}
		double cutoffRectangleSizeRatio = (cutOffSize / 100.0);
//		int nonCutOffWidth = (int) (totalRectangleWidth * (1 - cutoffRectangleSizeRatio));
		int nonCutOffWidth = (int) (totalRectangleWidth * (1 - 0));

		int zeroValues = (int) cells.stream().filter(c -> c.getAmount() == 0).count();
		if (zeroValues == cells.size() - nonCutoffNumber) {
			nonCutOffWidth = totalRectangleWidth;
		}
		long cutOffNumber = cells.size() - nonCutoffNumber;
		double nonCutOffSum = cells.stream().skip(cutOffNumber).mapToDouble(c -> Math.sqrt(c.getNormalizedAmount()))
				.sum();

		int minCellWidth = 3;
		Point end = new Point(rectEdge.x + totalRectangleWidth + 1, rectEdge.y);
		var start = end;
		var newRectangleWidth = 0;
		var cellIndex = cells.size() - 1;
		var selectedCells = new ArrayList<Cell>();
		int i = 0;
//		while (newRectangleWidth != nonCutOffWidth && i < nonCutoffNumber && cellIndex != -1) {
		while (i < nonCutoffNumber) {
			var cell = cells.get(cellIndex);

			var percentage = Math.sqrt(cell.getNormalizedAmount()) / nonCutOffSum;
			int cellWidth = Math.max((int) (nonCutOffWidth * percentage), minCellWidth);
			if (newRectangleWidth + cellWidth > nonCutOffWidth) {
				cellWidth = nonCutOffWidth - newRectangleWidth;
			}
			if(i == nonCutoffNumber-1)
				cellWidth = totalRectangleWidth - newRectangleWidth+1;

			newRectangleWidth += cellWidth;
			start = new Point(end.x - cellWidth, end.y);
			boolean isSelectedCell = cell.hasSameProduct(selectedCell)
					|| (cell.getProcess().category == chosenProcessCategory);

			cell.setBounds(start.x, start.y, cellWidth, rectHeight - 1);
			if (isSelectedCell) {
				int offset = 5;
				cell.width += 2 * offset;
				cell.height += 2 * offset;
				cell.x -= offset;
				cell.y -= offset;
				selectedCells.add(cell);
			} else {
				fillRectangle(gc, cell, SWT.COLOR_WHITE);
			}
			computeEndCell(start, cell, (int) cellWidth, false);
			end = start;
			cellIndex--;
			i++;
		}
		for (Cell cell : selectedCells) {
			fillRectangle(gc, cell, SWT.COLOR_WHITE);
			if (cell.equals(selectedCell)) {
				int borderWidth = 2;
				cell.width += borderWidth - 1;
				cell.height += borderWidth - 1;
				cell.x -= 1;
				cell.y -= 1;
				drawRectangle(gc, cell, borderWidth, SWT.COLOR_BLACK, SWT.COLOR_BLACK);
			}
		}
//		handleCutOff(cells, newRectangleWidth, rectEdge, gc, cellIndex + 1, totalRectangleWidth);
	}

	/**
	 * Handle the non focused values : they are in the cutoff area if we don't want
	 * them to be display by choosing an amount of process to be displayed
	 * 
	 * @param cells               The list of cells
	 * @param currentRectWidth    The remaining width where we can draw
	 * @param rectEdge            The left upper edge of the rectangle
	 * @param gc                  The GC component
	 * @param cutOffProcessAmount The amount of process in the cutoff area
	 * @param totalRectWidth      The length of the whole rectangle containing the
	 *                            cells
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
		int startIndex = (int) cells.stream().filter(c -> c.getAmount() == 0).count();
		if ((cutOffSize == 100 || cells.size() == cutOffProcessAmount) && startIndex != cells.size()) {
			cutOffWidth = totalRectWidth + 1;
		}

		double normalizedCutOffAmountSum = cells.stream().skip(startIndex).limit(cutOffProcessAmount)
				.mapToDouble(cell -> Math.abs(cell.getNormalizedAmount())).sum();
		double minimumGapBetweenCells = ((double) cutOffWidth / (cutOffProcessAmount - startIndex));
		int chunk = 0, chunkSize = 0, newChunk = 0;
		boolean gapBigEnough = true;
		if (minimumGapBetweenCells < 1.0) {
			// If the gap is to small, we put a certain amount of results in the same chunk
			chunkSize = (int) Math.ceil(1 / minimumGapBetweenCells);
			gapBigEnough = false;
		}
		Point end = null;
		var newRectWidth = 0;
		for (var cellIndex = startIndex; cellIndex < cutOffProcessAmount; cellIndex++) {
			if (!gapBigEnough) {
				newChunk = computeChunk(chunk, chunkSize, cellIndex);
			}
			var cell = cells.get(cellIndex);
			int cellWidth = 1;
			if (cellIndex == cutOffProcessAmount - 1) {
				cellWidth = (int) (cutOffWidth - newRectWidth);
			} else if (!gapBigEnough && chunk != newChunk) {
				// We are on a new chunk, so we draw a cell with an increasing width
				cellWidth++;
			} else if (!gapBigEnough && chunk == newChunk) {
				// We stay on the same chunk, so we don't draw the cell
				cellWidth = 0;
			} else {
				var value = cell.getNormalizedAmount();
				var percentage = value / normalizedCutOffAmountSum;
				cellWidth = (int) (cutOffWidth * percentage);
			}
			if (newRectWidth + cellWidth > cutOffWidth) {
				cellWidth = cutOffWidth - newRectWidth;
			}
			newRectWidth += cellWidth;
			end = computeEndCell(start, cell, (int) cellWidth, true);
			if (gapBigEnough || !gapBigEnough && chunk != newChunk) {
				// We end the current chunk / cell
				start = end;
				chunk = newChunk;
			}
		}
//		if (cutOffProcessAmount == cells.size()) {
//			newRectWidth = cutOffWidth;
//		}
		var cellRect = new Rectangle(rectEdge.x, rectEdge.y, cutOffWidth, rectHeight - 1);
		fillRectangle(gc, cellRect, rgbCutOff, SWT.COLOR_WHITE);
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
		var endingPoint = new Point(startingPoint.x, start.y - 2);
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
	private void fillRectangle(GC gc, Cell cell, Integer afterColor) {
		gc.setBackground(new Color(gc.getDevice(), cell.getRgb()));

		gc.fillRectangle(cell.x, cell.y, cell.width, cell.height);
		if (afterColor != null) {
			gc.setBackground(gc.getDevice().getSystemColor(afterColor));
		}
	}

	private void drawRectangle(GC gc, Cell cell, int borderWidth, Integer beforeColor, Integer afterColor) {
		gc.setForeground(gc.getDevice().getSystemColor((int) beforeColor));
		gc.setLineWidth(borderWidth);
		gc.drawRectangle(cell.x, cell.y, cell.width, cell.height);
		if (afterColor != null) {
			gc.setForeground(gc.getDevice().getSystemColor(afterColor));
		}
		gc.setLineWidth(1);
	}

	private void fillRectangle(GC gc, Rectangle cell, RGB cellColor, Integer afterColor) {
		gc.setBackground(new Color(gc.getDevice(), cellColor));

		gc.fillRectangle(cell.x, cell.y, cell.width, cell.height);
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
			var contributions = contributionsList.get(contributionsIndex);
			for (Cell cell : contributions.getList()) {
				if (!cell.isLinkDrawable()) // Sould not be in cutoff
					continue;
				var nextCells = contributionsList.get(contributionsIndex + 1);
				// We search for a cell that has the same process
				var optional = nextCells.getList().stream().filter(next -> next.getProcess().equals(cell.getProcess()))
						.findFirst();
				if (!optional.isPresent())
					continue;
				var linkedCell = optional.get();
				if (!linkedCell.isLinkDrawable())
					continue;
				var startPoint = cell.getStartingLinkPoint();
				var endPoint = linkedCell.getEndingLinkPoint();
				cell.addLinkNumber();
				linkedCell.addLinkNumber();
				if (cell.hasSameProduct(selectedCell) || cell.getProcess().category == chosenProcessCategory) {
					cell.setSelected(true);
					var polygon = getParallelogram(startPoint, endPoint, 5, contributions.bar);
					gc.setBackground(new Color(gc.getDevice(), cell.getRgb()));
					gc.fillPolygon(polygon);
					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
				} else {
					cell.setSelected(false);
					if (config.useBezierCurve) {
						drawBezierCurve(gc, startPoint, endPoint, cell.getRgb());
					} else {
						drawLine(gc, startPoint, endPoint, cell.getRgb(), SWT.COLOR_BLACK);
					}
				}
			}
		}
		return;
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
	private int[] getParallelogram(Point start, Point end, int width, Rectangle contributionsBar) {
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

		int array[] = { truncatePointCoordinate(r1.x, contributionsBar.x + contributionsBar.width, true),
				truncatePointCoordinate(r1.y, contributionsBar.y + contributionsBar.height + 1, true),
				truncatePointCoordinate(r2.x, contributionsBar.x + contributionsBar.width, true),
				truncatePointCoordinate(r2.y, contributionsBar.y + contributionsBar.height + 1, true),
				truncatePointCoordinate(r4.x, contributionsBar.x, false),
				truncatePointCoordinate(r4.y, contributionsBar.y + gapBetweenRect - 2, false),
				truncatePointCoordinate(r3.x, contributionsBar.x, false),
				truncatePointCoordinate(r3.y, contributionsBar.y + gapBetweenRect - 2, false) };
		return array;

	}

	private int truncatePointCoordinate(int p1, int p2, boolean upperTruncate) {
		if (upperTruncate)
			return p1 > p2 ? p2 : p1;
		else
			return p1 < p2 ? p2 : p1;
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
	private void addScrollListener() {
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
	private void addToolTipListener(Composite parent) {
		Listener mouseListener = new Listener() {
			public void handleEvent(Event event) {
				switch (event.type) {
				case SWT.MouseEnter:
				case SWT.MouseMove:
					for (Contributions contributions : contributionsList) {
						for (Cell cell : Lists.reverse(contributions.getList())) {
							// event contains the coordinate of the cursor, but we also have to take in
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
							// event contains the coordinate of the cursor, but we also have to take in
							// count if we scrolled
							var cursor = new Point(event.x - scrollPoint.x, event.y - scrollPoint.y);
							// If the cursor is contained in the cell
							if (cell.contains(cursor) && cell.isDisplayed()) {
								if (cell.equals(selectedCell)) {
									selectedCell = null;
								} else {
									selectedCell = cell;
								}
								updateCaption();
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

	private void updateCaption() {
		var child = captionBody.getChildren();
		for (Control control : child) {
			control.dispose();
		}
		if (selectedCell != null) {
			UI.gridLayout(captionBody, 2, 6, 10);
			InfoSection.link(captionBody, "Process", selectedCell.getProcess());
			InfoSection.link(captionBody, "Process category", selectedCell.getProcessCategory());
			InfoSection.link(captionBody, "Location", selectedCell.getLocation());
			InfoSection.link(captionBody, "Impact category", selectedCell.getImpactCategory());
			new Label(captionBody, SWT.NONE).setText("Contribution");
			var impactCategoryUnit = selectedCell.getContributions().getImpactCategory().referenceUnit;
			var contribution = "" + selectedCell.getContributionAmount();
			if (impactCategoryUnit != null)
				contribution += " " + impactCategoryUnit;
			new Label(captionBody, SWT.NONE).setText(contribution);
		} else {
			UI.gridLayout(captionBody, 2, 10, 10);
			InfoSection.link(captionBody, "Process", null);
			InfoSection.link(captionBody, "Process category", null);
			InfoSection.link(captionBody, "Location", null);
			InfoSection.link(captionBody, "Impact category", null);
			new Label(captionBody, SWT.NONE).setText("Contribution");
			new Label(captionBody, SWT.NONE).setText("");
		}
		captionBody.requestLayout();
	}

	/**
	 * Add a resize listener to the canvas. This is the entry point of the program
	 * 
	 * @param composite Parent component of the canvas
	 * @param canvas    The Canvas component
	 */
	private void addResizeEvent(Composite composite, Canvas canvas) {
		canvas.addListener(SWT.Resize, new Listener() {
			@Override
			public void handleEvent(Event e) {
				System.out.println("before");
				System.out.println(canvas.getSize());
				if (cacheMap.isEmpty()) {
					// If we didn't cached anything yet, we draw something
					redraw(composite, canvas);
					// FIXME
					// This is done to fix a bug on horizontal scrollbar
					canvas.notifyListeners(SWT.Resize, e);
				}
				handleScroll();
				System.out.println("after");
				System.out.println(canvas.getSize());
			}

		});
	}

	private void handleScroll() {
		Rectangle client = canvas.getClientArea();
		var hash = computeConfigurationHash();
		var cache = cacheMap.get(hash);
		int imageHeight = client.height;
		if (cache != null) {
			imageHeight = cache.getBounds().height;
		}
		var vBar = canvas.getVerticalBar();
		vBar.setThumb(Math.min(imageHeight, client.height));
		vBar.setPageIncrement(Math.min(imageHeight, client.height));
		vBar.setIncrement(20);
		vBar.setMaximum(imageHeight);
		var hBar = canvas.getHorizontalBar();
		hBar.setMinimum(0);
		hBar.setThumb(Math.min(canvasWidth, client.width));
		hBar.setPageIncrement(Math.min(canvasWidth, client.width));
		hBar.setIncrement(20);
		hBar.setMaximum(canvasWidth);

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

	/**
	 * Add a paint listener to the canvas. This is called whenever the canvas needs
	 * to be redrawed, then it draws the cached image
	 * 
	 * @param canvas A Canvas component
	 */
	private void addPaintListener() {
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				var hash = computeConfigurationHash();
				var cache = cacheMap.get(hash);

				if (cache != null) {
					e.gc.drawImage(cache, scrollPoint.x, scrollPoint.y);
				} else {
					// When we resize the screen, we don't have created the image yet, so we take
					// the current one
					e.gc.drawImage(cachedImage, scrollPoint.x, scrollPoint.y);
				}

			}
		});
	}

	private static double round(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(Double.toString(value));
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}
}