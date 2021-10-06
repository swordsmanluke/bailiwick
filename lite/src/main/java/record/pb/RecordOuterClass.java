// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: record.proto

package record.pb;

public final class RecordOuterClass {
  private RecordOuterClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  public interface RecordOrBuilder extends
      // @@protoc_insertion_point(interface_extends:record.pb.Record)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <pre>
     * The key that references this record
     * </pre>
     *
     * <code>bytes key = 1;</code>
     * @return The key.
     */
    com.google.protobuf.ByteString getKey();

    /**
     * <pre>
     * The actual value this record is storing
     * </pre>
     *
     * <code>bytes value = 2;</code>
     * @return The value.
     */
    com.google.protobuf.ByteString getValue();

    /**
     * <pre>
     * Time the record was received, set by receiver
     * </pre>
     *
     * <code>string timeReceived = 5;</code>
     * @return The timeReceived.
     */
    String getTimeReceived();
    /**
     * <pre>
     * Time the record was received, set by receiver
     * </pre>
     *
     * <code>string timeReceived = 5;</code>
     * @return The bytes for timeReceived.
     */
    com.google.protobuf.ByteString
        getTimeReceivedBytes();
  }
  /**
   * <pre>
   * Record represents a dht record that contains a value
   * for a key value pair
   * </pre>
   *
   * Protobuf type {@code record.pb.Record}
   */
  public static final class Record extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:record.pb.Record)
      RecordOrBuilder {
  private static final long serialVersionUID = 0L;
    // Use Record.newBuilder() to construct.
    private Record(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private Record() {
      key_ = com.google.protobuf.ByteString.EMPTY;
      value_ = com.google.protobuf.ByteString.EMPTY;
      timeReceived_ = "";
    }

    @Override
    @SuppressWarnings({"unused"})
    protected Object newInstance(
        UnusedPrivateParameter unused) {
      return new Record();
    }

    @Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return this.unknownFields;
    }
    private Record(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      if (extensionRegistry == null) {
        throw new NullPointerException();
      }
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10: {

              key_ = input.readBytes();
              break;
            }
            case 18: {

              value_ = input.readBytes();
              break;
            }
            case 42: {
              String s = input.readStringRequireUtf8();

              timeReceived_ = s;
              break;
            }
            default: {
              if (!parseUnknownField(
                  input, unknownFields, extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return RecordOuterClass.internal_static_record_pb_Record_descriptor;
    }

    @Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return RecordOuterClass.internal_static_record_pb_Record_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              Record.class, Builder.class);
    }

    public static final int KEY_FIELD_NUMBER = 1;
    private com.google.protobuf.ByteString key_;
    /**
     * <pre>
     * The key that references this record
     * </pre>
     *
     * <code>bytes key = 1;</code>
     * @return The key.
     */
    @Override
    public com.google.protobuf.ByteString getKey() {
      return key_;
    }

    public static final int VALUE_FIELD_NUMBER = 2;
    private com.google.protobuf.ByteString value_;
    /**
     * <pre>
     * The actual value this record is storing
     * </pre>
     *
     * <code>bytes value = 2;</code>
     * @return The value.
     */
    @Override
    public com.google.protobuf.ByteString getValue() {
      return value_;
    }

    public static final int TIMERECEIVED_FIELD_NUMBER = 5;
    private volatile Object timeReceived_;
    /**
     * <pre>
     * Time the record was received, set by receiver
     * </pre>
     *
     * <code>string timeReceived = 5;</code>
     * @return The timeReceived.
     */
    @Override
    public String getTimeReceived() {
      Object ref = timeReceived_;
      if (ref instanceof String) {
        return (String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        timeReceived_ = s;
        return s;
      }
    }
    /**
     * <pre>
     * Time the record was received, set by receiver
     * </pre>
     *
     * <code>string timeReceived = 5;</code>
     * @return The bytes for timeReceived.
     */
    @Override
    public com.google.protobuf.ByteString
        getTimeReceivedBytes() {
      Object ref = timeReceived_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        timeReceived_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private byte memoizedIsInitialized = -1;
    @Override
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      if (!key_.isEmpty()) {
        output.writeBytes(1, key_);
      }
      if (!value_.isEmpty()) {
        output.writeBytes(2, value_);
      }
      if (!getTimeReceivedBytes().isEmpty()) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 5, timeReceived_);
      }
      unknownFields.writeTo(output);
    }

    @Override
    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      if (!key_.isEmpty()) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(1, key_);
      }
      if (!value_.isEmpty()) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(2, value_);
      }
      if (!getTimeReceivedBytes().isEmpty()) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(5, timeReceived_);
      }
      size += unknownFields.getSerializedSize();
      memoizedSize = size;
      return size;
    }

    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof Record)) {
        return super.equals(obj);
      }
      Record other = (Record) obj;

      if (!getKey()
          .equals(other.getKey())) return false;
      if (!getValue()
          .equals(other.getValue())) return false;
      if (!getTimeReceived()
          .equals(other.getTimeReceived())) return false;
      if (!unknownFields.equals(other.unknownFields)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptor().hashCode();
      hash = (37 * hash) + KEY_FIELD_NUMBER;
      hash = (53 * hash) + getKey().hashCode();
      hash = (37 * hash) + VALUE_FIELD_NUMBER;
      hash = (53 * hash) + getValue().hashCode();
      hash = (37 * hash) + TIMERECEIVED_FIELD_NUMBER;
      hash = (53 * hash) + getTimeReceived().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static Record parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static Record parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Record parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static Record parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Record parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static Record parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Record parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static Record parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static Record parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static Record parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static Record parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static Record parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(Record prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * <pre>
     * Record represents a dht record that contains a value
     * for a key value pair
     * </pre>
     *
     * Protobuf type {@code record.pb.Record}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:record.pb.Record)
        RecordOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return RecordOuterClass.internal_static_record_pb_Record_descriptor;
      }

      @Override
      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return RecordOuterClass.internal_static_record_pb_Record_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                Record.class, Builder.class);
      }

      // Construct using record.pb.RecordOuterClass.Record.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      @Override
      public Builder clear() {
        super.clear();
        key_ = com.google.protobuf.ByteString.EMPTY;

        value_ = com.google.protobuf.ByteString.EMPTY;

        timeReceived_ = "";

        return this;
      }

      @Override
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return RecordOuterClass.internal_static_record_pb_Record_descriptor;
      }

      @Override
      public Record getDefaultInstanceForType() {
        return Record.getDefaultInstance();
      }

      @Override
      public Record build() {
        Record result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      @Override
      public Record buildPartial() {
        Record result = new Record(this);
        result.key_ = key_;
        result.value_ = value_;
        result.timeReceived_ = timeReceived_;
        onBuilt();
        return result;
      }

      @Override
      public Builder clone() {
        return super.clone();
      }
      @Override
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return super.setField(field, value);
      }
      @Override
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return super.clearField(field);
      }
      @Override
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return super.clearOneof(oneof);
      }
      @Override
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, Object value) {
        return super.setRepeatedField(field, index, value);
      }
      @Override
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return super.addRepeatedField(field, value);
      }
      @Override
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof Record) {
          return mergeFrom((Record)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(Record other) {
        if (other == Record.getDefaultInstance()) return this;
        if (other.getKey() != com.google.protobuf.ByteString.EMPTY) {
          setKey(other.getKey());
        }
        if (other.getValue() != com.google.protobuf.ByteString.EMPTY) {
          setValue(other.getValue());
        }
        if (!other.getTimeReceived().isEmpty()) {
          timeReceived_ = other.timeReceived_;
          onChanged();
        }
        this.mergeUnknownFields(other.unknownFields);
        onChanged();
        return this;
      }

      @Override
      public final boolean isInitialized() {
        return true;
      }

      @Override
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        Record parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (Record) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }

      private com.google.protobuf.ByteString key_ = com.google.protobuf.ByteString.EMPTY;
      /**
       * <pre>
       * The key that references this record
       * </pre>
       *
       * <code>bytes key = 1;</code>
       * @return The key.
       */
      @Override
      public com.google.protobuf.ByteString getKey() {
        return key_;
      }
      /**
       * <pre>
       * The key that references this record
       * </pre>
       *
       * <code>bytes key = 1;</code>
       * @param value The key to set.
       * @return This builder for chaining.
       */
      public Builder setKey(com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        key_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * The key that references this record
       * </pre>
       *
       * <code>bytes key = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearKey() {
        
        key_ = getDefaultInstance().getKey();
        onChanged();
        return this;
      }

      private com.google.protobuf.ByteString value_ = com.google.protobuf.ByteString.EMPTY;
      /**
       * <pre>
       * The actual value this record is storing
       * </pre>
       *
       * <code>bytes value = 2;</code>
       * @return The value.
       */
      @Override
      public com.google.protobuf.ByteString getValue() {
        return value_;
      }
      /**
       * <pre>
       * The actual value this record is storing
       * </pre>
       *
       * <code>bytes value = 2;</code>
       * @param value The value to set.
       * @return This builder for chaining.
       */
      public Builder setValue(com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        value_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * The actual value this record is storing
       * </pre>
       *
       * <code>bytes value = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearValue() {
        
        value_ = getDefaultInstance().getValue();
        onChanged();
        return this;
      }

      private Object timeReceived_ = "";
      /**
       * <pre>
       * Time the record was received, set by receiver
       * </pre>
       *
       * <code>string timeReceived = 5;</code>
       * @return The timeReceived.
       */
      public String getTimeReceived() {
        Object ref = timeReceived_;
        if (!(ref instanceof String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          String s = bs.toStringUtf8();
          timeReceived_ = s;
          return s;
        } else {
          return (String) ref;
        }
      }
      /**
       * <pre>
       * Time the record was received, set by receiver
       * </pre>
       *
       * <code>string timeReceived = 5;</code>
       * @return The bytes for timeReceived.
       */
      public com.google.protobuf.ByteString
          getTimeReceivedBytes() {
        Object ref = timeReceived_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (String) ref);
          timeReceived_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <pre>
       * Time the record was received, set by receiver
       * </pre>
       *
       * <code>string timeReceived = 5;</code>
       * @param value The timeReceived to set.
       * @return This builder for chaining.
       */
      public Builder setTimeReceived(
          String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        timeReceived_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * Time the record was received, set by receiver
       * </pre>
       *
       * <code>string timeReceived = 5;</code>
       * @return This builder for chaining.
       */
      public Builder clearTimeReceived() {
        
        timeReceived_ = getDefaultInstance().getTimeReceived();
        onChanged();
        return this;
      }
      /**
       * <pre>
       * Time the record was received, set by receiver
       * </pre>
       *
       * <code>string timeReceived = 5;</code>
       * @param value The bytes for timeReceived to set.
       * @return This builder for chaining.
       */
      public Builder setTimeReceivedBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        timeReceived_ = value;
        onChanged();
        return this;
      }
      @Override
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.setUnknownFields(unknownFields);
      }

      @Override
      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return super.mergeUnknownFields(unknownFields);
      }


      // @@protoc_insertion_point(builder_scope:record.pb.Record)
    }

    // @@protoc_insertion_point(class_scope:record.pb.Record)
    private static final Record DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new Record();
    }

    public static Record getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<Record>
        PARSER = new com.google.protobuf.AbstractParser<Record>() {
      @Override
      public Record parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new Record(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<Record> parser() {
      return PARSER;
    }

    @Override
    public com.google.protobuf.Parser<Record> getParserForType() {
      return PARSER;
    }

    @Override
    public Record getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_record_pb_Record_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_record_pb_Record_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    String[] descriptorData = {
      "\n\014record.proto\022\trecord.pb\":\n\006Record\022\013\n\003k" +
      "ey\030\001 \001(\014\022\r\n\005value\030\002 \001(\014\022\024\n\014timeReceived\030" +
      "\005 \001(\tb\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_record_pb_Record_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_record_pb_Record_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_record_pb_Record_descriptor,
        new String[] { "Key", "Value", "TimeReceived", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}