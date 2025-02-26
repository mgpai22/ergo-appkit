package org.ergoplatform.appkit.impl;

import com.google.gson.Gson;

import org.ergoplatform.ErgoBox;
import org.ergoplatform.appkit.*;
import org.ergoplatform.explorer.client.model.OutputInfo;
import org.ergoplatform.restapi.client.ErgoTransactionOutput;
import org.ergoplatform.restapi.client.JSON;

import java.util.Arrays;
import java.util.List;

import sigmastate.Values;
import sigmastate.interpreter.ContextExtension;
import special.sigma.Box;

public class InputBoxImpl implements InputBox {
    private final ErgoId _id;
    private final ErgoBox _ergoBox;
    private final ErgoTransactionOutput _boxData;
    private ContextExtension _extension;

    public InputBoxImpl(ErgoTransactionOutput boxData) {
        _id = new ErgoId(JavaHelpers.decodeStringToBytes(boxData.getBoxId()));
        _ergoBox = ScalaBridge.isoErgoTransactionOutput().to(boxData);
        _boxData = boxData;
        _extension = ContextExtension.empty();
    }

    public InputBoxImpl(OutputInfo outputInfo) {
        _id = ErgoId.create(outputInfo.getBoxId());
        _ergoBox = ScalaBridge.isoExplTransactionOutput().to(outputInfo);
        _boxData = ScalaBridge.isoErgoTransactionOutput().from(_ergoBox);
        _extension = ContextExtension.empty();
    }

    public InputBoxImpl(ErgoBox ergoBox) {
        _ergoBox = ergoBox;
        _id = new ErgoId((byte[])ergoBox.id());
        _boxData = ScalaBridge.isoErgoTransactionOutput().from(ergoBox);
        _extension = ContextExtension.empty();
    }

    @Override
    public ErgoId getId() {
        return _id;
    }

    @Override
    public long getValue() {
        return _ergoBox.value();
    }

    @Override
    public int getCreationHeight() {
      return _ergoBox.creationHeight();
    }

    @Override
    public List<ErgoToken> getTokens() {
        List<ErgoToken> tokens = Iso.isoTokensListToPairsColl().from(_ergoBox.additionalTokens());
        return tokens;
    }

    @Override
    public List<ErgoValue<?>> getRegisters() {
        return JavaHelpers.getBoxRegisters(_ergoBox);
    }

    @Override
    public Values.ErgoTree getErgoTree() {
        return _ergoBox.ergoTree();
    }

    @Override
    public BoxAttachment getAttachment() {
        return BoxAttachmentBuilder.buildFromTransactionBox(this);
    }

    @Override
    public InputBox withContextVars(ContextVar... variables) {
        ContextExtension extension = Iso.isoContextVarsToContextExtension().to(
            Arrays.asList(variables)
        );
        InputBoxImpl res =  new InputBoxImpl(_ergoBox);
        res._extension = extension;
        return res;
    }

    @Override
    public String toJson(boolean prettyPrint) {
        return toJson(prettyPrint, true);
    }

    @Override
    public String toJson(boolean prettyPrint, boolean formatJson) {
    	Gson gson = (prettyPrint || formatJson) ? JSON.createGson().setPrettyPrinting().create() : JSON.createGson().create();
    	ErgoTransactionOutput data = _boxData;
    	if (prettyPrint) {
    		data = cloneDataObject(gson, _boxData);
    		data.ergoTree(_ergoBox.ergoTree().toString());
    	}
    	String json = gson.toJson(data);
    	return json;
    }

    private  <T> T cloneDataObject(Gson gson, T dataObj) {
        return (T)gson.fromJson(gson.toJson(dataObj), dataObj.getClass());
    }

    @Override
    public byte[] getBytes() { return _ergoBox.bytes(); }

    @Override
    public String getTransactionId() {
        return (String) _ergoBox.transactionId();
    }

    @Override
    public int getTransactionIndex() {
        return _ergoBox.index();
    }

    public ErgoBox getErgoBox() {
        return _ergoBox;
    }

    public ContextExtension getExtension() {
        return _extension;
    }

    @Override
    public String toString() {
        return String.format("InputBox(%s, %s)", getId(), getValue());
    }

    @Override
    public ErgoValue<Box> toErgoValue() {
        return ErgoValue.of(getErgoBox());
    }
}
