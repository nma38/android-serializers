package serializers.cks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cks.value.data.Maybe;
import data.media.MediaTransformer;
import serializers.cks.media.Image;
import serializers.cks.media.Media;
import serializers.cks.media.MediaContent;
import serializers.cks.media.Pod;

public class Cks
{
	// ------------------------------------------------------------
	// Transformers

	public static final MediaTransformer<MediaContent> mediaTransformer = new MediaTransformer<MediaContent>()
	{
	    @Override
	    public MediaContent[] resultArray(int size) { return new MediaContent[size]; }
	    
	    // ----------------------------------------------------------
		// Forward

		public MediaContent forward(data.media.MediaContent mc)
		{
			ArrayList<Image> images = new ArrayList<Image>();
			for (data.media.Image image : mc.images) {
				images.add(forwardImage(image));
			}

			return new MediaContent(images, forwardMedia(mc.media));
		}

		private Pod forwardPod(data.media.Pod pod) {

			return new Pod(
					pod.getMessage(),
					pod.getPod() != null ? Maybe.Just(forwardPod(pod.getPod())) : Maybe.<Pod>Nothing()
			);
		}

		private Media forwardMedia(data.media.Media media)
		{
			ArrayList<Pod> pods = new ArrayList<>();
			for (data.media.Pod pod : media.getPods()) {
				pods.add(forwardPod(pod));
			}

			return new Media(
				media.uri,
				media.title != null ? Maybe.Just(media.title) : Maybe.<String>Nothing(),
				media.width,
				media.height,
				media.format,
				media.duration,
				media.size,
				media.hasBitrate ? Maybe.Just(media.bitrate) : Maybe.<Integer>Nothing(),
				new ArrayList<String>(media.persons),
				forwardPlayer(media.player),
				media.copyright != null ? Maybe.Just(media.copyright) : Maybe.<String>Nothing(),
				pods
			);
		}

		public Media.Player forwardPlayer(data.media.Media.Player p)
		{
			switch (p) {
				case JAVA: return Media.Player.Java_.mk();
				case FLASH: return Media.Player.Flash_.mk();
				default:
					throw new AssertionError("invalid case: " + p);
			}
		}

		private Image forwardImage(data.media.Image image)
		{
			return new Image(
				image.uri,
				image.title != null ? Maybe.Just(image.title) : Maybe.<String>Nothing(),
				image.width,
				image.height,
				forwardSize(image.size)
			);
		}

		public Image.Size forwardSize(data.media.Image.Size s)
		{
			switch (s) {
				case SMALL: return Image.Size.Small_.mk();
				case LARGE: return Image.Size.Large_.mk();
				default:
					throw new AssertionError("invalid case: " + s);
			}
		}

		// ----------------------------------------------------------
		// Reverse

		public data.media.MediaContent reverse(MediaContent mc)
		{
			List<data.media.Image> images = new ArrayList<data.media.Image>(mc.images.size());

			for (Image image : mc.images) {
				images.add(reverseImage(image));
			}

			return new data.media.MediaContent(reverseMedia(mc.media), images);
		}

		private data.media.Media reverseMedia(Media media)
		{
            ArrayList<data.media.Pod> pods = new ArrayList<>();
            for (Pod pod : media.pods) {
                pods.add(reversePod(pod));
            }

			// Media
			return new data.media.Media(
				media.uri,
				media.title.get(null),
				media.width,
				media.height,
				media.format,
				media.duration,
				media.size,
				media.bitrate.get(0),
				media.bitrate.isJust(),
				new ArrayList<String>(media.persons),
				reversePlayer(media.player),
				media.copyright.get(null),
                pods
			);
		}

		public data.media.Media.Player reversePlayer(Media.Player p)
		{
			if (p instanceof Media.Player.Java_) return data.media.Media.Player.JAVA;
			if (p instanceof Media.Player.Flash_) return data.media.Media.Player.FLASH;
			throw new AssertionError("invalid case: " + p);
		}

		private data.media.Image reverseImage(Image image)
		{
			return new data.media.Image(
				image.uri,
				image.title.get(null),
				image.width,
				image.height,
				reverseSize(image.size));
		}

		private data.media.Pod reversePod(Pod pod) {
			return new data.media.Pod(
                    pod.message,
                    !pod.pod.isNothing() ? reversePod(pod.pod.get(null)) : null
			);
		}

		public data.media.Image.Size reverseSize(Image.Size s)
		{
			if (s instanceof Image.Size.Small_) return data.media.Image.Size.SMALL;
			if (s instanceof Image.Size.Large_) return data.media.Image.Size.LARGE;
			throw new AssertionError("invalid case: " + s);
		}

		public data.media.MediaContent shallowReverse(MediaContent mc)
		{
			return new data.media.MediaContent(reverseMedia(mc.media), Collections.<data.media.Image>emptyList());
		}
	};
}
