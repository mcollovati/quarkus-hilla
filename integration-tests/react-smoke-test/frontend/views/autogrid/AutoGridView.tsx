import { AutoGrid } from '@hilla/react-crud';
import { UserService } from 'Frontend/generated/endpoints';
import UserPOJOModel from 'Frontend/generated/com/example/application/autogrid/UserPOJOModel';

export default function AutoGridView() {
    return <AutoGrid service={UserService} model={UserPOJOModel} />;
}


