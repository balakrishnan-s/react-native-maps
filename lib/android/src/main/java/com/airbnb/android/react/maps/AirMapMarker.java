package com.airbnb.android.react.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.util.LruCache;
import android.view.View;
import android.widget.LinearLayout;

import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.generic.GenericDraweeHierarchy;
import com.facebook.drawee.generic.GenericDraweeHierarchyBuilder;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.DraweeHolder;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.image.CloseableStaticBitmap;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import javax.annotation.Nullable;

public class AirMapMarker extends AirMapFeature {
  private static Bitmap DEFAULT_BITMAP = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
  private static BitmapDescriptor DEFAULT_BITMAP_DESCRIPTOR
      = BitmapDescriptorFactory.fromBitmap(DEFAULT_BITMAP);

  private static int cacheSize = 4 * 1024 * 1024;

  private static final LruCache<String, BitmapDescriptorHolder> bitmapCache
      = new LruCache<String, BitmapDescriptorHolder>(cacheSize) { };

  private class BitmapDescriptorHolder {
    private final String hash;
    private final Bitmap bitmap;
    private final BitmapDescriptor bitmapDescriptor;

    private BitmapDescriptorHolder(Bitmap bitmap, BitmapDescriptor bitmapDescriptor, String hash) {
      this.bitmap = bitmap;
      this.bitmapDescriptor = bitmapDescriptor;
      this.hash = hash;
    }
  }

  private MarkerOptions markerOptions;
  private Marker marker;
  private int width;
  private int height;
  private String identifier;

  private LatLng position;
  private String title;
  private String snippet;

  private boolean anchorIsSet;
  private float anchorX;
  private float anchorY;

  private AirMapCallout calloutView;
  private View wrappedCalloutView;
  private final Context context;

  private float markerHue = 0.0f; // should be between 0 and 360
  private BitmapDescriptor iconBitmapDescriptor;
  private Bitmap iconBitmap;

  private float rotation = 0.0f;
  private boolean flat = false;
  private boolean draggable = false;
  private int zIndex = 0;
  private float opacity = 1.0f;

  private float calloutAnchorX;
  private float calloutAnchorY;
  private boolean calloutAnchorIsSet;

  private boolean hasCustomMarkerView = false;

  private String uri;
  private final DraweeHolder<?> logoHolder;
  private DataSource<CloseableReference<CloseableImage>> dataSource;
  private final ControllerListener<ImageInfo> mLogoControllerListener =
      new BaseControllerListener<ImageInfo>() {
        @Override
        public void onFinalImageSet(
            String id,
            @Nullable final ImageInfo imageInfo,
            @Nullable Animatable animatable) {
          CloseableReference<CloseableImage> imageReference = null;
          try {

            imageReference = dataSource.getResult();
            if (imageReference == null) return;

            CloseableImage image = imageReference.get();
            if (image == null || !(image instanceof CloseableStaticBitmap)) return;

            CloseableStaticBitmap closeableStaticBitmap = (CloseableStaticBitmap) image;

            synchronized (bitmapCache) {
              if (bitmapCache.get(uri) == null) {
                Bitmap bitmap = closeableStaticBitmap.getUnderlyingBitmap();
                if (bitmap == null) return;
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                iconBitmap = bitmap;
                iconBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(iconBitmap);
                bitmapCache.put(uri, new BitmapDescriptorHolder(
                    iconBitmap, iconBitmapDescriptor, uri));
                return;
              } else {
                iconBitmap = bitmapCache.get(uri).bitmap;
                iconBitmapDescriptor = bitmapCache.get(uri).bitmapDescriptor;
              }
            }
          } finally {
            dataSource.close();
            if (imageReference != null) {
              CloseableReference.closeSafely(imageReference);
            }
          }
          update();
        }
      };

  public AirMapMarker(Context context) {
    super(context);
    this.context = context;
    logoHolder = DraweeHolder.create(createDraweeHierarchy(), context);
    logoHolder.onAttach();
  }

  private GenericDraweeHierarchy createDraweeHierarchy() {
    return new GenericDraweeHierarchyBuilder(getResources())
        .setActualImageScaleType(ScalingUtils.ScaleType.FIT_CENTER)
        .setFadeDuration(0)
        .build();
  }

  public void setCoordinate(ReadableMap coordinate) {
    position = new LatLng(coordinate.getDouble("latitude"), coordinate.getDouble("longitude"));
    if (marker != null) {
      marker.setPosition(position);
    }
    update();
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
    update();
  }

  public String getIdentifier() {
    return this.identifier;
  }

  public void setTitle(String title) {
    this.title = title;
    if (marker != null) {
      marker.setTitle(title);
    }
    update();
  }

  public void setSnippet(String snippet) {
    this.snippet = snippet;
    if (marker != null) {
      marker.setSnippet(snippet);
    }
    update();
  }

  public void setRotation(float rotation) {
    this.rotation = rotation;
    if (marker != null) {
      marker.setRotation(rotation);
    }
    update();
  }

  public void setFlat(boolean flat) {
    this.flat = flat;
    if (marker != null) {
      marker.setFlat(flat);
    }
    update();
  }

  public void setDraggable(boolean draggable) {
    this.draggable = draggable;
    if (marker != null) {
      marker.setDraggable(draggable);
    }
    update();
  }

  public void setZIndex(int zIndex) {
    this.zIndex = zIndex;
    if (marker != null) {
      marker.setZIndex(zIndex);
    }
    update();
  }

  public void setOpacity(float opacity) {
    this.opacity = opacity;
    if (marker != null) {
      marker.setAlpha(opacity);
    }
    update();
  }

  public void setMarkerHue(float markerHue) {
    this.markerHue = markerHue;
    update();
  }

  public void setAnchor(double x, double y) {
    anchorIsSet = true;
    anchorX = (float) x;
    anchorY = (float) y;
    if (marker != null) {
      marker.setAnchor(anchorX, anchorY);
    }
    update();
  }

  public void setCalloutAnchor(double x, double y) {
    calloutAnchorIsSet = true;
    calloutAnchorX = (float) x;
    calloutAnchorY = (float) y;
    if (marker != null) {
      marker.setInfoWindowAnchor(calloutAnchorX, calloutAnchorY);
    }
    update();
  }

  public void setImage(String uri) {
    if (uri == null) {
      iconBitmapDescriptor = null;
      update();
    } else if (uri.startsWith("http://") || uri.startsWith("https://") ||
        uri.startsWith("file://")) {

      this.uri = uri;

      synchronized (bitmapCache) {
        if (bitmapCache.get(uri) != null) {
          iconBitmap = bitmapCache.get(uri).bitmap;
          iconBitmapDescriptor = bitmapCache.get(uri).bitmapDescriptor;
          update();
          return;
        }
      }

      ImageRequest imageRequest = ImageRequestBuilder
          .newBuilderWithSource(Uri.parse(uri))
          .build();

      ImagePipeline imagePipeline = Fresco.getImagePipeline();
      dataSource = imagePipeline.fetchDecodedImage(imageRequest, this);
      DraweeController controller = Fresco.newDraweeControllerBuilder()
          .setImageRequest(imageRequest)
          .setControllerListener(mLogoControllerListener)
          .setOldController(logoHolder.getController())
          .build();
      logoHolder.setController(controller);
    } else {
      iconBitmapDescriptor = getBitmapDescriptorByName(uri);
      if (iconBitmapDescriptor != null) {
          iconBitmap = BitmapFactory.decodeResource(getResources(), getDrawableResourceByName(uri));
      }
      update();
    }
  }

  public MarkerOptions getMarkerOptions() {
    if (markerOptions == null) {
      markerOptions = createMarkerOptions();
    }
    return markerOptions;
  }

  @Override
  public void addView(View child, int index) {
    super.addView(child, index);
    // if children are added, it means we are rendering a custom marker
    if (!(child instanceof AirMapCallout)) {
      hasCustomMarkerView = true;
    }
    update();
  }

  @Override
  public Object getFeature() {
    return marker;
  }

  @Override
  public void addToMap(GoogleMap map) {
    marker = map.addMarker(getMarkerOptions());
  }

  @Override
  public void removeFromMap(GoogleMap map) {
    marker.remove();
    marker = null;
  }

  private BitmapDescriptor getIcon() {
    if (hasCustomMarkerView) {
      // creating a bitmap from an arbitrary view
      if (iconBitmapDescriptor != null) {
        BitmapDescriptorHolder viewBitmapDescriptorHolder = createDrawable();

        if (iconBitmap != null && viewBitmapDescriptorHolder != null) {
          Bitmap viewBitmap = viewBitmapDescriptorHolder.bitmap;

          int width = Math.max(iconBitmap.getWidth(), viewBitmap.getWidth());
          int height = Math.max(iconBitmap.getHeight(), viewBitmap.getHeight());
          Bitmap combinedBitmap = Bitmap.createBitmap(width, height, iconBitmap.getConfig());

          Canvas canvas = new Canvas(combinedBitmap);
          canvas.drawBitmap(iconBitmap, 0, 0, null);
          canvas.drawBitmap(viewBitmap, 0, 0, null);

          String hash = uri + viewBitmapDescriptorHolder.hash;

          synchronized (bitmapCache) {
            if (bitmapCache.get(hash) == null) {
              BitmapDescriptorHolder holder = new BitmapDescriptorHolder(combinedBitmap,
                  BitmapDescriptorFactory.fromBitmap(combinedBitmap), hash);

              bitmapCache.put(hash, holder);
            }

            return bitmapCache.get(hash).bitmapDescriptor;
          }
        } else if (viewBitmapDescriptorHolder != null) {
          return viewBitmapDescriptorHolder.bitmapDescriptor;
        } else if (iconBitmap != null) {
          return iconBitmapDescriptor;
        } else {
          return DEFAULT_BITMAP_DESCRIPTOR;
        }
      } else {
        BitmapDescriptorHolder viewHolder = createDrawable();
        if (viewHolder != null) {
          return viewHolder.bitmapDescriptor;
        }

        return DEFAULT_BITMAP_DESCRIPTOR;
      }
    } else if (iconBitmapDescriptor != null) {
      // use local image as a marker
      return iconBitmapDescriptor;
    } else {
      // render the default marker pin
      return BitmapDescriptorFactory.defaultMarker(this.markerHue);
    }
  }

  private MarkerOptions createMarkerOptions() {
    MarkerOptions options = new MarkerOptions().position(position);
    if (anchorIsSet) options.anchor(anchorX, anchorY);
    if (calloutAnchorIsSet) options.infoWindowAnchor(calloutAnchorX, calloutAnchorY);
    options.title(title);
    options.snippet(snippet);
    options.rotation(rotation);
    options.flat(flat);
    options.draggable(draggable);
    options.zIndex(zIndex);
    options.alpha(opacity);
    options.icon(getIcon());
    return options;
  }

  public void update() {
    if (marker == null) {
      return;
    }

    marker.setIcon(getIcon());

    if (anchorIsSet) {
      marker.setAnchor(anchorX, anchorY);
    } else {
      marker.setAnchor(0.5f, 1.0f);
    }

    if (calloutAnchorIsSet) {
      marker.setInfoWindowAnchor(calloutAnchorX, calloutAnchorY);
    } else {
      marker.setInfoWindowAnchor(0.5f, 0);
    }
  }

  public void update(int width, int height) {
    this.width = width;
    this.height = height;
    update();
  }

  private BitmapDescriptorHolder createDrawable() {
    if (this.width <= 0 || this.height <= 0) {
      return null;
    }

    int width = this.width;
    int height = this.height;

    this.buildDrawingCache();
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

    Canvas canvas = new Canvas(bitmap);
    this.draw(canvas);

    String hash = Long.toString(hashBitmap(bitmap));

    synchronized (bitmapCache) {
      if (bitmapCache.get(hash) == null) {
        BitmapDescriptorHolder holder = new BitmapDescriptorHolder(bitmap,
            BitmapDescriptorFactory.fromBitmap(bitmap), hash);

        bitmapCache.put(hash, holder);
      }

      return bitmapCache.get(hash);
    }
  }

  public void setCalloutView(AirMapCallout view) {
    this.calloutView = view;
  }

  public AirMapCallout getCalloutView() {
    return this.calloutView;
  }

  public View getCallout() {
    if (this.calloutView == null) return null;

    if (this.wrappedCalloutView == null) {
      this.wrapCalloutView();
    }

    if (this.calloutView.getTooltip()) {
      return this.wrappedCalloutView;
    } else {
      return null;
    }
  }

  public View getInfoContents() {
    if (this.calloutView == null) return null;

    if (this.wrappedCalloutView == null) {
      this.wrapCalloutView();
    }

    if (this.calloutView.getTooltip()) {
      return null;
    } else {
      return this.wrappedCalloutView;
    }
  }

  private void wrapCalloutView() {
    // some hackery is needed to get the arbitrary infowindow view to render centered, and
    // with only the width/height that it needs.
    if (this.calloutView == null || this.calloutView.getChildCount() == 0) {
      return;
    }

    LinearLayout LL = new LinearLayout(context);
    LL.setOrientation(LinearLayout.VERTICAL);
    LL.setLayoutParams(new LinearLayout.LayoutParams(
        this.calloutView.width,
        this.calloutView.height,
        0f
    ));


    LinearLayout LL2 = new LinearLayout(context);
    LL2.setOrientation(LinearLayout.HORIZONTAL);
    LL2.setLayoutParams(new LinearLayout.LayoutParams(
        this.calloutView.width,
        this.calloutView.height,
        0f
    ));

    LL.addView(LL2);
    LL2.addView(this.calloutView);

    this.wrappedCalloutView = LL;
  }

  private int getDrawableResourceByName(String name) {
    return getResources().getIdentifier(
        name,
        "drawable",
        getContext().getPackageName());
  }

  private BitmapDescriptor getBitmapDescriptorByName(String name) {
    return BitmapDescriptorFactory.fromResource(getDrawableResourceByName(name));
  }

  private static long PRIME = 31;
  private static long hashBitmap(Bitmap bmp){
    long hash = PRIME;
    for(int x = 0; x < bmp.getWidth(); x++){
      for (int y = 0; y < bmp.getHeight(); y++){
        hash += (bmp.getPixel(x, y) + PRIME);
      }
    }

    return hash;
  }
}
