package serializers.flatbuffers;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import data.media.MediaTransformer;
import serializers.SerClass;
import serializers.SerFeatures;
import serializers.SerFormat;
import serializers.SerGraph;
import serializers.Serializer;
import serializers.TestGroups;
import serializers.flatbuffers.media.Image;
import serializers.flatbuffers.media.Media;
import serializers.flatbuffers.media.MediaContent;
import serializers.flatbuffers.media.Player;
import serializers.flatbuffers.media.Pod;
import serializers.flatbuffers.media.Size;

/**
 * @author nickma
 * @version 1.0
 * @since 02/02/16
 */
public class Flatbuffers {
    public static void register(TestGroups groups) {
        groups.media.add(new Transformer(), new PBSerializer(),
                new SerFeatures(
                        SerFormat.BIN_CROSSLANG,
                        SerGraph.FLAT_TREE,
                        SerClass.MANUAL_OPT,
                        ""
                ));
    }

    static final class PBSerializer extends Serializer<MediaContent>
    {
        public String getName() { return "flatbuffers"; }

        @Override
        public MediaContent deserialize (byte[] array) throws Exception {
            ByteBuffer bb = ByteBuffer.wrap(array);
            return MediaContent.getRootAsMediaContent(bb);
        }

        @Override
        public byte[] serialize(MediaContent content) throws IOException {
            return content.getByteBuffer().compact().array();
        }

        @Override
        public final void serializeItems(MediaContent[] items, OutputStream out0) throws IOException
        {
            DataOutputStream out = new DataOutputStream(out0);
            for (MediaContent item : items) {
                byte[] data = serialize(item);
                out.writeInt(data.length);
                out.write(data);
            }
            // should we write end marker (length of 0) or not? For now, omit it
            out.flush();
        }

        @Override
        public MediaContent[] deserializeItems(InputStream in0, int numberOfItems) throws Exception
        {
            DataInputStream in = new DataInputStream(in0);
            MediaContent[] result = new MediaContent[numberOfItems];
            for (int i = 0; i < numberOfItems; ++i) {
                int len = in.readInt();
                byte[] data = new byte[len];
                in.readFully(data);
                in.close();
                result[i] = deserialize(data);
            }
            return result;
        }
    }

    public static final void readFullyDirectly(FlatBufferBuilder fbsBuilder, InputStream in, int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = in.read();
            fbsBuilder.addByte((byte) count);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    public static final class Transformer extends MediaTransformer<MediaContent>
    {
        @Override
        public MediaContent[] resultArray(int size) { return new MediaContent[size]; }

        // ----------------------------------------------------------
        // Forward

        @Override
        public MediaContent forward(data.media.MediaContent mc)
        {
            FlatBufferBuilder builder = new FlatBufferBuilder();

            // Media
            int mediaOffset = forwardMedia(mc.getMedia(), builder);

            int[] imageOffsets = new int[mc.images.size()];
            for (int i = 0; i < mc.images.size(); i++) {
                imageOffsets[i] = forwardImage(mc.images.get(i), builder);
            }

            int imageOffset = MediaContent.createImageVector(builder, imageOffsets);

            MediaContent.startMediaContent(builder);
            MediaContent.addMedia(builder, mediaOffset);
            MediaContent.addImage(builder, imageOffset);
            int rootTableOffset = MediaContent.endMediaContent(builder);

            MediaContent.finishMediaContentBuffer(builder, rootTableOffset);
            return MediaContent.getRootAsMediaContent(builder.dataBuffer());
        }

        private int forwardMedia(data.media.Media media, FlatBufferBuilder builder)
        {
            int[] personVectorOffsets = new int[media.persons.size()];
            for (int i = 0; i < media.persons.size(); i++) {
                personVectorOffsets[i] = builder.createString(media.persons.get(i));
            }

            int[] podVectorOffsets = new int[media.pods.size()];
            for (int i = 0; i < media.pods.size(); i++) {
                podVectorOffsets[i] = forwardPod(media.pods.get(i), builder);
            }
            // Media
            int mediaOffset = Media.createMedia(builder,
                    builder.createString(media.uri),
                    media.title != null ? builder.createString(media.title) : builder.createString(ByteBuffer.allocate(0)),
                    media.width,
                    media.height,
                    builder.createString(media.format),
                    media.duration,
                    media.size,
                    media.bitrate,
                    Media.createPersonVector(builder, personVectorOffsets),
                    forwardPlayer(media.player),
                    media.copyright != null ? builder.createString(media.copyright) : builder.createString(ByteBuffer.allocate(0)),
                    Media.createPodsVector(builder, podVectorOffsets)
            );
            return mediaOffset;
        }

        private int forwardPod(data.media.Pod pod, FlatBufferBuilder builder)
        {
            int innerPodOffset = Integer.MIN_VALUE;
            if (pod.getPod() != null) {
                innerPodOffset = forwardPod(pod.getPod(), builder);
            }
            int messageOffset = builder.createString(pod.getMessage());
            Pod.startPod(builder);
            if (innerPodOffset != Integer.MIN_VALUE) {
                Pod.addPod(builder, innerPodOffset);
            }
            Pod.addMessage(builder, messageOffset);
            return Pod.endPod(builder);
        }

        public Byte forwardPlayer(data.media.Media.Player p)
        {
            switch (p) {
                case JAVA: return Player.JAVA;
                case FLASH: return Player.FLASH;
                default:
                    throw new AssertionError("invalid case: " + p);
            }
        }

        private int forwardImage(data.media.Image image, FlatBufferBuilder builder)
        {
            int imageOffset = Image.createImage(builder,
                    builder.createString(image.uri),
                    image.title != null ? builder.createString(image.title) : builder.createString(ByteBuffer.allocate(0)),
                    image.width,
                    image.height,
                    forwardSize(image.size)
                    );

            return imageOffset;
        }

        public Byte forwardSize(data.media.Image.Size s)
        {
            switch (s) {
                case SMALL: return Size.SMALL;
                case LARGE: return Size.LARGE;
                default:
                    throw new AssertionError("invalid case: " + s);
            }
        }

        // ----------------------------------------------------------
        // Reverse

        @Override
        public data.media.MediaContent reverse(MediaContent mc)
        {
            List<data.media.Image> images = new ArrayList<>(mc.imageLength());

            for (int i = 0; i < mc.imageLength(); i++) {
                images.add(reverseImage(mc.image(i)));
            }

            data.media.Media media = reverseMedia(mc.media());
            return new data.media.MediaContent(media, images);
        }

        private data.media.Media reverseMedia(Media media)
        {
            ArrayList<String> persons = new ArrayList<>();
            for (int i = 0; i < media.personLength(); i++) {
                persons.add(media.person(i));
            }

            ArrayList<data.media.Pod> pods = new ArrayList<>();
            for (int i = 0; i < media.podsLength(); i++) {
                pods.add(reversePod(media.pods(i)));
            }
            // Media
            return new data.media.Media(
                    media.uri(),
                    media.title().isEmpty() ? null : media.title(),
                    media.width(),
                    media.height(),
                    media.format(),
                    media.duration(),
                    media.size(),
                    media.bitrate(),
                    media.bitrate() > 1 ? true:false,
                    persons,
                    reversePlayer(media.player()),
                    media.copyright().isEmpty() ? null : media.copyright(),
                    pods
            );
        }

        private data.media.Pod reversePod(Pod pod) {
            data.media.Pod innerPod = null;
            String message = pod.message();
            if (pod.pod() != null) {
                innerPod = reversePod(pod.pod());
            }
            return new data.media.Pod(
                    message,
                    innerPod
            );
        }

        public data.media.Media.Player reversePlayer(Byte p)
        {
            if (p == Player.JAVA) return data.media.Media.Player.JAVA;
            if (p == Player.FLASH) return data.media.Media.Player.FLASH;
            throw new AssertionError("invalid case: " + p);
        }

        private data.media.Image reverseImage(Image image)
        {
            return new data.media.Image(
                    image.uri(),
                    image.title().isEmpty() ? null : image.title(),
                    image.width(),
                    image.height(),
                    reverseSize(image.size()));
        }

        public data.media.Image.Size reverseSize(Byte s)
        {
            switch (s) {
                case Size.SMALL: return data.media.Image.Size.SMALL;
                case Size.LARGE: return data.media.Image.Size.LARGE;
                default:
                    throw new AssertionError("invalid case: " + s);
            }
        }

        public data.media.MediaContent shallowReverse(MediaContent mc)
        {
            return new data.media.MediaContent(reverseMedia(mc.media()), Collections.<data.media.Image>emptyList());
        }
    };
}
